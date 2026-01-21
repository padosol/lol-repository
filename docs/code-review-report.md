# Code Review Report - LOL Repository Project

**검토 일시**: 2026-01-16
**검토 도구**: Claude Code code-improver agent
**검토 파일**: 20개 이상 (domain entities, repositories, listeners, services)

---

## Review Summary

| 구분 | 개수 |
|------|------|
| Critical | 3 |
| Important | 9 |
| Minor | 8 |
| Suggestion | 4 |
| **총 제안사항** | **24** |

### Top 3 Priorities
1. **Critical N+1 Query Performance Issue** - MatchRepository.findAllMatchIdByIdsNotIn
2. **Memory and Performance Issue** - MatchSummonerEntity builder pattern (100+ fields)
3. **Duplicate Logic and Transaction Management** - SummonerWriter service

### Overall Assessment
코드베이스는 Spring Boot와 JPA에 대한 이해도가 높으며, CompletableFuture를 활용한 비동기 처리와 Redis 기반 Rate Limiting이 잘 구현되어 있습니다. 하지만 몇 가지 중요한 성능 이슈, 코드 중복, 그리고 유지보수성과 효율성을 개선할 수 있는 아키텍처 패턴 기회가 있습니다.

---

## Critical Issues

### 1. N+1 Query Problem in MatchRepository
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/match/repository/MatchRepository.java` (Lines 21-29)

**문제점**: 동일한 쿼리를 2번 실행하여 불필요한 데이터베이스 왕복 발생. `isEmpty()` 체크도 불필요함.

**현재 코드**:
```java
public List<String> findAllMatchIdByIdsNotIn(Collection<String> matchIds) {
    List<MatchEntity> allByMatchIdIsNotIn = matchJpaRepository.findAllByMatchIdIsNotIn(matchIds);

    if (allByMatchIdIsNotIn.isEmpty()) {
        return new ArrayList<>();
    }

    return matchJpaRepository.findAllByMatchIdIsNotIn(matchIds).stream().map(MatchEntity::getMatchId).toList();
}
```

**개선 코드**:
```java
public List<String> findAllMatchIdByIdsNotIn(Collection<String> matchIds) {
    return matchJpaRepository.findAllByMatchIdIsNotIn(matchIds)
            .stream()
            .map(MatchEntity::getMatchId)
            .toList();
}
```

**개선 효과**:
- DB 호출 50% 감소
- 불필요한 isEmpty() 체크 제거
- 더 깔끔한 함수형 코드 스타일

---

### 2. Massive Builder Pattern in MatchSummonerEntity
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/match/entity/MatchSummonerEntity.java` (Lines 203-342)

**문제점**: `of()` 메서드가 138줄 이상의 builder 호출로 구성됨. 엔티티가 190개 이상의 필드를 가져 단일 책임 원칙 위반.

**개선 방안**: 관련 필드를 그룹화하여 private 메서드로 분리

```java
public static MatchSummonerEntity of(MatchEntity match, ParticipantDto dto) {
    MatchSummonerEntity entity = new MatchSummonerEntity();

    entity.setBasicInfo(dto, match);
    entity.setChampionInfo(dto);
    entity.setKillStats(dto);
    entity.setGoldAndItems(dto);
    entity.setMinionStats(dto);
    entity.setWardStats(dto);
    entity.setObjectiveStats(dto);
    entity.setGameInfo(dto);
    entity.setDamageStats(dto);
    entity.setArenaStats(dto);
    entity.setEmbeddedValues(dto);

    return entity;
}
```

---

### 3. Massive Code Duplication in SummonerWriter
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/summoner/service/SummonerWriter.java` (Lines 28-77)

**문제점**: 두 개의 `saveSummonerData` 메서드가 90% 동일한 코드를 가짐. DRY 원칙 위반.

**개선 방안**: 공통 로직을 private 메서드로 추출

```java
@Transactional
public void saveSummonerData(AccountDto accountDto, SummonerDto summonerDto,
                             Set<LeagueEntryDto> leagueEntryDtos, Platform platform) {
    SummonerEntity summonerEntity = new SummonerEntity(accountDto, summonerDto, platform);
    saveSummonerWithLeague(summonerEntity, accountDto.getPuuid(), leagueEntryDtos);
}

@Transactional
public void saveSummonerData(Summoner summoner) {
    SummonerEntity summonerEntity = SummonerEntity.of(summoner);
    saveSummonerWithLeague(summonerEntity, summoner.getPuuid(), summoner.getLeagueEntryDtos());
}

private void saveSummonerWithLeague(SummonerEntity summonerEntity, String puuid,
                                    Set<LeagueEntryDto> leagueEntryDtos) {
    summonerEntity.initRevisionDate();
    summonerRepository.save(summonerEntity);

    leagueEntryDtos.forEach(leagueEntryDto ->
        saveOrUpdateLeagueSummoner(puuid, leagueEntryDto)
    );
}
```

---

## Important Issues

### 4. Missing Null Safety in LeagueSummonerEntity
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/league/entity/LeagueSummonerEntity.java` (Lines 113-118)

**문제점**: `calculatePoints()` 메서드가 null 또는 유효하지 않은 tier/rank 값 처리 누락

**개선 코드**:
```java
private int calculatePoints() {
    if (this.tier == null || this.rank == null) {
        log.warn("Tier or rank is null for league summoner calculation. Using league points only.");
        return this.leaguePoints;
    }

    try {
        int tierScore = Tier.valueOf(this.tier).getScore();
        int divisionScore = Division.valueOf(this.rank).getScore();
        return divisionScore + tierScore + this.leaguePoints;
    } catch (IllegalArgumentException e) {
        log.error("Invalid tier '{}' or rank '{}' for league summoner", this.tier, this.rank, e);
        return this.leaguePoints;
    }
}
```

---

### 5. Hardcoded Season Value in MatchService
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/match/service/MatchService.java` (Line 38)

**문제점**: 시즌 값 "26"이 하드코딩됨

**개선 방안**: application.yml 설정으로 외부화

```java
@Value("${lol.current-season:26}")
private int currentSeason;
```

```yaml
# application.yml
lol:
  current-season: 26
```

---

### 6. Missing Build Assignment in MatchTeamEntity
**Location**: `lol-core/src/main/java/com/mmrtr/lol/domain/match/entity/MatchTeamEntity.java` (Lines 69-83)

**문제점**: Builder가 생성되었지만 build되지 않음. bans 리스트에 대한 null 체크 및 배열 경계 검사 누락.

**개선 코드**:
```java
List<BanDto> bans = teamDto.getBans();
TeamBanValue teamBanValue = null;

if (bans != null && bans.size() >= 5) {
    teamBanValue = TeamBanValue.builder()
            .champion1Id(bans.get(0).getChampionId())
            .pick1Turn(bans.get(0).getPickTurn())
            // ... 나머지 필드들
            .build();
}

return MatchTeamEntity.builder()
        .matchId(match.getMatchId())
        .teamId(teamDto.getTeamId())
        .win(teamDto.isWin())
        .teamObject(teamObjectValue)
        .teamBan(teamBanValue)  // 실제로 할당
        .build();
```

---

### 7. Assert in Production Code
**Location**: `lol-core/src/main/java/com/mmrtr/lol/rabbitmq/listener/SummonerRenewalListener.java` (Line 59)

**문제점**: 프로덕션 코드에서 `assert` 사용. JVM에서 기본적으로 비활성화됨.

**개선 코드**:
```java
if (summonerDto == null) {
    log.error("Failed to fetch summoner data for puuid: {}", puuid);
    redisLockHandler.releaseLock(puuid);
    throw new IllegalStateException("Summoner data is null for puuid: " + puuid);
}
```

---

### 8. Memory Issue with Large Match Lists
**Location**: `lol-core/src/main/java/com/mmrtr/lol/rabbitmq/listener/MatchFindListener.java` (Lines 45-69)

**문제점**: 최대 1000개의 매치 ID를 메모리에 한번에 로드

**개선 방안**: 배치 처리로 메모리 사용량 제한

```java
private static final int MATCH_BATCH_PROCESS_SIZE = 200;

// 배치 단위로 처리
if (currentBatch.size() >= MATCH_BATCH_PROCESS_SIZE) {
    processMatchBatch(currentBatch, platform);
    currentBatch.clear();
}
```

---

### 9. Transaction Scope Too Large
**Location**: `lol-core/src/main/java/com/mmrtr/lol/rabbitmq/listener/SummonerRenewalListener.java` (Lines 44-120)

**문제점**: 전체 리스너 메서드가 `@Transactional`로 감싸져 있어 외부 API 호출 동안 DB 연결 유지

**개선 방안**: API 호출과 DB 작업 분리

```java
@RabbitListener(queues = "mmrtr.summoner")
public void receiveSummonerMessageV2(@Payload SummonerMessage summonerMessage) {
    // Fetch data (외부 트랜잭션)
    SummonerRenewalData data = fetchSummonerData(summonerMessage);

    // DB 작업만 트랜잭션으로
    saveSummonerData(data);
}

@Transactional
private void saveSummonerData(SummonerRenewalData data) {
    // DB 작업만
}
```

---

## Minor Issues

### 10. Empty For-loop
**Location**: `MatchService.java` (Lines 71-73)

```java
// 현재: 빈 for-loop
for (TimelineDto timelineDto : timelineDtos) {

}

// 개선: TODO 주석 또는 파라미터 제거
```

### 11. Typo in Column Name
**Location**: `MatchSummonerEntity.java` (Line 32)

```java
// 현재
@Column(name = "match_sumoner_id")

// 개선
@Column(name = "match_summoner_id")
```

### 12. Inefficient String Concatenation
**Location**: `ChallengesEntity.java` (Lines 162-169)

```java
// 현재: StringBuffer 사용
StringBuffer sb = new StringBuffer();
for (Integer integer : challengesDto.getLegendaryItemUsed()) {
    if(!sb.isEmpty()) { sb.append(","); }
    sb.append(integer);
}

// 개선: Stream API 사용
String legendaryItems = challengesDto.getLegendaryItemUsed()
    .stream()
    .map(String::valueOf)
    .collect(Collectors.joining(","));
```

### 13. Magic Numbers
**Location**: `MatchFindListener.java` (Lines 46-51)

```java
// 현재
int offset = 20;
int count = 100;
while (retry < 10 && hasMoreMatches) {

// 개선
private static final int MATCH_FETCH_INITIAL_OFFSET = 20;
private static final int MATCH_FETCH_PAGE_SIZE = 100;
private static final int MAX_FETCH_RETRIES = 10;
```

### 14. Null Instead of Optional
**Location**: `LeagueRepository.java` (Lines 27-29)

```java
// 현재
public LeagueEntity findById(String leagueId) {
    return leagueJpaRepository.findById(leagueId).orElse(null);
}

// 개선
public Optional<LeagueEntity> findById(String leagueId) {
    return leagueJpaRepository.findById(leagueId);
}
```

### 15. Confusing Method Names
**Location**: `LeagueSummonerRepository.java` (Lines 24-30)

```java
// 현재: findAll*이 단일 엔티티 반환
public LeagueSummonerEntity findAllByPuuid(String puuid, String queue)

// 개선
public Optional<LeagueSummonerEntity> findByPuuidAndQueue(String puuid, String queue)
```

### 16. SQL Column Name Typo
**Location**: `MatchRepository.java` (Line 54)

```java
// 현재
"date_version,"

// 개선
"data_version,"
```

### 17. Wrong Variable Name
**Location**: `MatchSummonerRepository.java` (Line 325)

```java
// 현재: copy-paste로 인한 잘못된 변수명
.map(comment -> {

// 개선
.map(matchSummoner -> {
```

---

## Suggestions

### 18. @Setter on Value Objects
**Location**: `ItemValue.java`, `StatValue.java`, `StyleValue.java`

Value Object에서 `@Setter` 제거하여 불변성 유지

### 19. Missing Logging in MatchService
서비스에 로깅 추가하여 모니터링 및 디버깅 용이하게 개선

### 20. Missing Validation in MatchEntity Constructor
DTO에서 엔티티 생성 시 유효성 검사 추가

### 21. @LastModifiedBy → @LastModifiedDate
**Location**: `LeagueSummonerEntity.java` (Line 62)

타임스탬프 필드에 올바른 어노테이션 사용

---

## Positive Patterns Found

- CompletableFuture를 활용한 비동기 처리
- Redisson을 통한 분산 Rate Limiting
- JDBC Template을 활용한 배치 삽입 최적화
- `ON CONFLICT DO NOTHING`으로 멱등성 보장
- `@Embeddable` Value Objects 패턴

---

## Architecture Recommendations

1. **MapStruct 도입**: 대규모 수동 매핑을 DTO-to-Entity 매퍼 라이브러리로 대체
2. **Base Repository 추출**: 공통 repository 패턴을 기반 클래스로 추출
3. **Event-Driven Architecture**: 매치 처리를 느슨한 결합의 이벤트 기반으로 전환 고려
4. **Retry Logic 구현**: 외부 API 호출에 exponential backoff 적용
5. **Circuit Breaker 패턴**: RIOT API 호출에 Resilience4j 적용 고려

---

## Testing Recommendations

1. 배치 삽입 작업에 대한 통합 테스트 추가
2. 부하 상태에서의 Rate Limiting 동작 테스트
3. 트랜잭션 롤백 시나리오 테스트
4. null/유효하지 않은 데이터 처리 테스트
5. 대용량 배치 작업 성능 테스트
