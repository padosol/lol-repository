# 소환사 랭킹 시스템 메모리 최적화 계획

## 문제 상황

20만명 유저 데이터 처리 시 메모리 부담:
- **현재 피크 메모리**: 550-600 MB
- **이전 랭킹 조회**: `Map<String, Integer>` 20만건 (~23MB + Entity 로딩 ~200MB)
- **문제점**: OOM 위험, GC 부하

---

## 권장 솔루션: DB 레벨 rankChange 계산 + 페이징

### 핵심 아이디어
이전 랭킹을 애플리케이션 메모리에 로드하지 않고, **DB 내에서 직접 계산**

### 처리 흐름
```
1. 현재 랭킹 → 백업 테이블 복사 (INSERT ... SELECT)
2. 기존 데이터 삭제 (DELETE)
3. 새 랭킹 저장 - 페이징 처리, rankChange = 0 (INSERT)
4. rankChange 일괄 계산 (UPDATE ... FROM backup)
5. 백업 테이블 정리 (DELETE)
```

---

## 구현 단계

### 1단계: 백업 테이블 DDL

```sql
CREATE TABLE summoner_ranking_backup (
    puuid VARCHAR(100) NOT NULL,
    queue VARCHAR(50) NOT NULL,
    current_rank INTEGER NOT NULL,
    PRIMARY KEY (puuid, queue)
);
```

### 2단계: Repository 인터페이스 확장

**파일**: `core/domain/.../league/repository/SummonerRankingRepositoryPort.java`

```java
public interface SummonerRankingRepositoryPort {
    void saveAll(List<SummonerRanking> rankings);
    void bulkSaveAll(List<SummonerRanking> rankings);
    void deleteByQueue(String queue);

    // 신규 메서드
    void backupCurrentRanks(String queue);
    void updateRankChangesFromBackup(String queue);
    void clearBackup(String queue);
}
```

### 3단계: JPA Repository Native Query 추가

**파일**: `infra/persistence/.../league/repository/SummonerRankingJpaRepository.java`

```java
@Modifying
@Query(value = """
    INSERT INTO summoner_ranking_backup (puuid, queue, current_rank)
    SELECT puuid, queue, current_rank FROM summoner_ranking WHERE queue = :queue
    """, nativeQuery = true)
void backupCurrentRanks(@Param("queue") String queue);

@Modifying
@Query(value = """
    UPDATE summoner_ranking sr
    SET rank_change = COALESCE(backup.current_rank - sr.current_rank, 0)
    FROM summoner_ranking_backup backup
    WHERE sr.puuid = backup.puuid
      AND sr.queue = backup.queue
      AND sr.queue = :queue
    """, nativeQuery = true)
void updateRankChangesFromBackup(@Param("queue") String queue);

@Modifying
@Query(value = "DELETE FROM summoner_ranking_backup WHERE queue = :queue", nativeQuery = true)
void clearBackup(@Param("queue") String queue);
```

### 4단계: Repository 구현

**파일**: `infra/persistence/.../league/repository/SummonerRankingRepositoryImpl.java`

```java
@Override
public void backupCurrentRanks(String queue) {
    jpaRepository.backupCurrentRanks(queue);
}

@Override
public void updateRankChangesFromBackup(String queue) {
    jpaRepository.updateRankChangesFromBackup(queue);
}

@Override
public void clearBackup(String queue) {
    jpaRepository.clearBackup(queue);
}
```

### 5단계: Scheduler 로직 변경

**파일**: `infra/persistence/.../league/scheduler/SummonerRankingScheduler.java`

```java
private static final int PAGE_SIZE = 5000;

private void processQueueRanking(String queue) {
    // 1. 현재 랭킹을 백업 테이블로 복사
    summonerRankingRepositoryPort.backupCurrentRanks(queue);

    // 2. 기존 데이터 삭제
    summonerRankingRepositoryPort.deleteByQueue(queue);

    // 3. 전체 건수 확인
    long totalCount = leagueSummonerJpaRepository.countRankingByQueue(queue);
    if (totalCount == 0) {
        summonerRankingRepositoryPort.clearBackup(queue);
        return;
    }

    // 4. 페이지별 처리 (rankChange = 0으로 저장)
    int totalPages = (int) Math.ceil((double) totalCount / PAGE_SIZE);
    int currentRank = 1;

    for (int page = 0; page < totalPages; page++) {
        Page<SummonerRankingProjection> projectionPage =
            leagueSummonerJpaRepository.findRankingByQueuePaged(
                queue, PageRequest.of(page, PAGE_SIZE));

        List<String> puuids = projectionPage.getContent().stream()
            .map(SummonerRankingProjection::getPuuid).toList();
        Map<String, List<String>> mostChampionsMap = getMostChampionsMap(puuids);

        List<SummonerRanking> rankings = new ArrayList<>();
        for (SummonerRankingProjection p : projectionPage.getContent()) {
            rankings.add(SummonerRanking.builder()
                .puuid(p.getPuuid())
                .queue(queue)
                .currentRank(currentRank++)
                .rankChange(0)  // 임시로 0
                // ... 나머지 필드
                .build());
        }

        summonerRankingRepositoryPort.bulkSaveAll(rankings);
        rankings.clear();
    }

    // 5. rankChange 일괄 UPDATE (DB 레벨)
    summonerRankingRepositoryPort.updateRankChangesFromBackup(queue);

    // 6. 백업 테이블 정리
    summonerRankingRepositoryPort.clearBackup(queue);

    // 7. 티어 커트라인 처리
    // ...
}
```

---

## 수정 대상 파일

| 파일 | 변경 내용 |
|-----|---------|
| `SummonerRankingRepositoryPort.java` | 백업/업데이트 메서드 추가 |
| `SummonerRankingJpaRepository.java` | Native Query 3개 추가 |
| `SummonerRankingRepositoryImpl.java` | 메서드 구현 |
| `LeagueSummonerJpaRepository.java` | 페이징 쿼리 추가 |
| `SummonerRankingScheduler.java` | 로직 전체 재구성 |

---

## 메모리 사용 비교

| 항목 | 현재 | 개선 후 |
|-----|-----|--------|
| 이전 랭킹 조회 | ~223 MB | **0 MB** |
| 랭킹 데이터 처리 | ~550 MB | **25-40 MB** (5,000건 배치) |
| **총 피크 메모리** | **~550 MB** | **~40 MB** |

---

## 트레이드오프

| 항목 | 현재 | 개선 후 |
|-----|-----|--------|
| 피크 메모리 | 550 MB | **25-40 MB** |
| DB 쿼리 수 | 4회 | ~45회 |
| 처리 시간 | ~30초 | ~60-90초 |
| OOM 위험 | 높음 | **없음** |
| 백업 테이블 | 없음 | 필요 |

---

## 검증 방법

1. **빌드 확인**: `./gradlew build`
2. **메모리 테스트**: `-Xmx128m`으로 JVM 힙 제한 후 실행
3. **데이터 정합성**: rankChange 값이 올바르게 계산되는지 확인
4. **백업 테이블 정리**: 스케줄러 완료 후 backup 테이블이 비어있는지 확인
