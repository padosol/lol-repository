# 랭킹 계산 방식 변경 - 구현 및 리팩토링 보고서

## 1. 개요

### 배경
기존 랭킹 계산은 `league_summoner` 테이블(개별 소환사 API 호출로 적재된 데이터)을 기반으로, 모든 Platform을 순회하며 MASTER/GRANDMASTER/CHALLENGER 티어를 조회하고 정렬하는 방식이었습니다.

### 변경 목적
Riot League V4의 Challenger/Grandmaster 전용 엔드포인트를 직접 호출하여, KR 서버의 RANKED_SOLO_5x5 챌린저+그랜드마스터 랭킹을 더 정확하고 실시간성 있게 수집합니다.

### 변경 범위
- **신규 파일**: 4개
- **수정 파일**: 6개
- **변경량**: +200줄 / -117줄

---

## 2. 아키텍처 변경

### Before
```
SummonerRankingScheduler (2시간 주기)
  └─ SummonerRankingCalculationService.processQueueRanking()
       └─ LeagueSummonerJpaRepository  ← league_summoner 테이블 (JPA JOIN SummonerEntity)
            └─ 모든 Platform 순회 → MASTER/GM/CHALLENGER 필터 → absolutePoints 정렬
                 └─ 페이지별 랭킹 빌드 → bulkSaveAll
```

### After
```
SummonerRankingScheduler (2시간 주기)
  └─ SummonerRankingCalculationService.processQueueRanking()
       └─ LeagueApiPort.getApexEntries()  ← Riot API 직접 호출 (KR만)
            └─ LeagueApiAdapter
                 ├─ getChallengerLeague  ─┐
                 └─ getGrandmasterLeague ─┘─ CompletableFuture.allOf() (병렬)
       └─ SummonerRepositoryPort.findAllByPuuidIn()  ← DB 배치 조회
       └─ SummonerService.getSummonerByPuuid()  ← 미등록 소환사 500ms 간격 조회
       └─ 병합 → 정렬 → 랭킹 빌드 → bulkSaveAll
```

### 핵심 변경점

| 항목 | Before | After |
|------|--------|-------|
| 데이터 소스 | `league_summoner` 테이블 | Riot League V4 API 직접 호출 |
| 대상 플랫폼 | 모든 Platform enum 순회 | KR 고정 |
| 대상 큐 | RANKED_SOLO_5x5 + RANKED_FLEX_SR | RANKED_SOLO_5x5만 |
| 대상 티어 | MASTER/GM/CHALLENGER | CHALLENGER/GRANDMASTER만 |
| 소환사 정보 | JPA JOIN으로 동시 조회 | 배치 조회 + 미등록자 API 개별 조회 |
| API 호출 | 없음 (DB만) | 챌린저+그마 API 병렬 호출 |

---

## 3. 신규 파일 상세

### 3-1. `LeagueListDto` (infra:riot-client)
```
infra/riot-client/src/main/java/com/mmrtr/lol/infra/riot/dto/league/LeagueListDto.java
```
- Riot League V4 `/challengerleagues`, `/grandmasterleagues` 응답 매핑 DTO
- `ErrorDTO` 상속 (기존 `LeagueEntryDto` 패턴과 동일)
- 필드: `tier`, `leagueId`, `queue`, `name`, `List<LeagueItemDto> entries`

### 3-2. `LeagueItemDto` (infra:riot-client)
```
infra/riot-client/src/main/java/com/mmrtr/lol/infra/riot/dto/league/LeagueItemDto.java
```
- `LeagueListDto.entries` 내부 아이템 DTO
- 필드: `puuid`, `summonerId`, `leaguePoints`, `rank`, `wins`, `losses`, `veteran`, `inactive`, `freshBlood`, `hotStreak`

### 3-3. `LeagueApiPort` (core:domain)
```
core/domain/src/main/java/com/mmrtr/lol/domain/league/application/port/LeagueApiPort.java
```
- 헥사고날 아키텍처 포트 인터페이스
- `infra:persistence` → `infra:riot-client` 직접 의존을 방지
- 단일 메서드: `Map<Tier, List<LeagueEntry>> getApexEntries(String queue, String platformName)`
- 내부 record `LeagueEntry`로 인프라 DTO를 도메인 경계에서 격리

### 3-4. `LeagueApiAdapter` (infra:riot-client)
```
infra/riot-client/src/main/java/com/mmrtr/lol/infra/riot/adapter/LeagueApiAdapter.java
```
- `LeagueApiPort` 구현체
- `CompletableFuture.allOf()`로 챌린저/그마 API **병렬 호출**
- `LeagueListDto` → `LeagueEntry` 변환 처리

---

## 4. 수정 파일 상세

### 4-1. `RiotApiService` (infra:riot-client)
```
infra/riot-client/src/main/java/com/mmrtr/lol/infra/riot/service/RiotApiService.java
```
- 추가: `getApexLeague(String tierPath, String queue, Platform platform, Executor executor)`
- `tierPath` 파라미터로 `challengerleagues`/`grandmasterleagues` 구분 → 중복 제거

### 4-2. `SummonerRepositoryPort` (core:domain)
```
core/domain/src/main/java/com/mmrtr/lol/domain/summoner/application/port/SummonerRepositoryPort.java
```
- 추가: `Map<String, Summoner> findAllByPuuidIn(Collection<String> puuids)`

### 4-3. `SummonerJpaRepository` (infra:persistence)
```
infra/persistence/src/main/java/com/mmrtr/lol/infra/persistence/summoner/repository/SummonerJpaRepository.java
```
- 추가: `List<SummonerEntity> findAllByPuuidIn(Collection<String> puuids)` (Spring Data 자동 쿼리)

### 4-4. `SummonerRepositoryImpl` (infra:persistence)
```
infra/persistence/src/main/java/com/mmrtr/lol/infra/persistence/summoner/repository/SummonerRepositoryImpl.java
```
- 추가: `findAllByPuuidIn` 구현 (JPA 결과 → Summoner 도메인 변환 → puuid 키 Map)

### 4-5. `SummonerRankingCalculationService` (infra:persistence) - **핵심 변경**
```
infra/persistence/src/main/java/com/mmrtr/lol/infra/persistence/league/scheduler/SummonerRankingCalculationService.java
```

**제거된 의존성:**
- `LeagueSummonerJpaRepository` (league_summoner 테이블 기반 조회)
- `SummonerRankingProjection` (JPA 프로젝션)
- `Platform` 순회 로직

**추가된 의존성:**
- `LeagueApiPort` (Riot API 챌린저/그마 조회)
- `SummonerRepositoryPort` (배치 소환사 조회)
- `SummonerService` (미등록 소환사 개별 조회)

**새로운 processQueueRanking 흐름:**
1. 백업 (기존 동일) → clearBackup → backupCurrentRanks → backupCurrentCutoffs → deleteByQueue
2. Riot API 병렬 호출 → `leagueApiPort.getApexEntries(queue, "KR")`
3. 엔트리 병합/정렬 → `RankedEntry.of()`로 absolutePoints 미리 계산
4. 소환사 배치 조회 → `summonerRepositoryPort.findAllByPuuidIn()`
5. 미등록 소환사 개별 조회 → `summonerService.getSummonerByPuuid()` (500ms 간격)
6. 랭킹 빌드 → 모스트 챔피언 + winRate + SummonerRanking 빌드 → bulkSaveAll
7. 랭크 변동 계산 (기존 동일) → updateRankChangesFromBackup → clearBackup
8. 티어 커트라인 저장 (기존 동일) → saveTierCutoffUseCase → updateLpChangesFromBackup

### 4-6. `SummonerRankingScheduler` (infra:persistence)
```
infra/persistence/src/main/java/com/mmrtr/lol/infra/persistence/league/scheduler/SummonerRankingScheduler.java
```
- `QUEUE_TYPES`에서 `RANKED_FLEX_SR` 제거 → `RANKED_SOLO_5x5`만 유지

---

## 5. 리팩토링 내용 (/simplify 코드 리뷰 반영)

3개 리뷰 에이전트(Code Reuse, Code Quality, Efficiency)의 피드백을 반영하여 6건의 개선을 적용했습니다.

### 5-1. Stringly-typed 코드 → Tier enum 사용
- **Before**: `new RankedEntry(entry, "CHALLENGER")`, `"CHALLENGER".equals(tier)`
- **After**: `RankedEntry.of(entry, Tier.CHALLENGER)`, `Tier.CHALLENGER == tier`
- **효과**: 컴파일 타임 타입 안전성 확보, try/catch 기반 `Tier.valueOf()` 제거

### 5-2. API 메서드 중복 제거
- **Before**: `getChallengerLeague()` + `getGrandmasterLeague()` (RiotApiService에 거의 동일한 메서드 2개)
- **After**: `getApexLeague(String tierPath, ...)` 단일 메서드
- **효과**: URL path만 다른 중복 코드 제거, 추후 Master 티어 추가 시 호출부만 추가하면 됨

### 5-3. Challenger/Grandmaster API 병렬 호출
- **Before**: `getChallengerEntries()` → `.join()` → `getGrandmasterEntries()` → `.join()` (순차 실행)
- **After**: `CompletableFuture.allOf(challengerFuture, grandmasterFuture).join()` (병렬 실행)
- **효과**: 두 독립적인 네트워크 호출의 총 대기 시간 약 50% 감소
- **포트 통합**: `getChallengerEntries()` + `getGrandmasterEntries()` → `getApexEntries()` 단일 메서드

### 5-4. computeIfAbsent 반환값 직접 사용
- **Before**: `result.computeIfAbsent(puuid, k -> new ArrayList<>());` + `List<String> champions = result.get(puuid);`
- **After**: `List<String> champions = result.computeIfAbsent(puuid, k -> new ArrayList<>());`
- **효과**: HashMap에 대한 불필요한 이중 조회 제거

### 5-5. Sort 비교자 내 반복 계산 제거
- **Before**: `Comparator.comparingInt(e -> calculateAbsolutePoints(e.tier, e.entry.leaguePoints()))` → 정렬 시 O(n log n)회 `Tier.valueOf()` 호출
- **After**: `RankedEntry` record에 `absolutePoints` 필드 추가, 생성 시점에 1회 계산
  ```java
  private record RankedEntry(LeagueEntry entry, Tier tier, int absolutePoints) {
      static RankedEntry of(LeagueEntry entry, Tier tier) {
          return new RankedEntry(entry, tier, tier.getScore() + entry.leaguePoints());
      }
  }
  ```
- **효과**: ~1000건 기준 약 10,000회 enum 조회 → 1,000회로 감소

### 5-6. 불필요한 중간 리스트 제거
- **Before**: `allPuuids` 리스트를 별도로 생성한 뒤, 다시 `unknownPuuids`를 동일 소스에서 필터링
- **After**: `unknownPuuids`를 `mergedEntries`에서 직접 도출
- **효과**: ~1000개 String 객체의 불필요한 중간 리스트 할당 제거

---

## 6. 재사용한 기존 코드

| 기존 코드 | 위치 | 용도 |
|-----------|------|------|
| `matchSummonerJpaRepository.findMostChampionsByPuuids()` | infra:persistence | 모스트 챔피언 top 3 조회 |
| `getMostChampionsMap()` | SummonerRankingCalculationService | 프로젝션 → Map 변환 |
| `summonerRankingRepositoryPort` (backup/restore 전체 패턴) | core:domain port | 랭크 변동 계산 |
| `tierCutoffRepositoryPort` (backup/restore 전체 패턴) | core:domain port | LP 변동 계산 |
| `saveTierCutoffUseCase` | core:domain usecase | 티어 커트라인 저장 |
| `SummonerService.getSummonerByPuuid()` | core:domain | 미등록 소환사 Riot API 조회 + DB 저장 |
| `Tier` enum (score 값) | core:enum | absolutePoints 계산 |
| `Platform.KR` | core:enum | platformHost/platformId 참조 |

---

## 7. 파일 변경 요약

| 구분 | 파일 | 변경 내용 |
|------|------|----------|
| 신규 | `infra/riot-client/.../dto/league/LeagueListDto.java` | Riot API 응답 DTO |
| 신규 | `infra/riot-client/.../dto/league/LeagueItemDto.java` | 엔트리 아이템 DTO |
| 신규 | `core/domain/.../league/application/port/LeagueApiPort.java` | 포트 인터페이스 (Tier 키 Map 반환) |
| 신규 | `infra/riot-client/.../adapter/LeagueApiAdapter.java` | 어댑터 (병렬 API 호출) |
| 수정 | `infra/riot-client/.../service/RiotApiService.java` | `getApexLeague()` 추가 (+13줄) |
| 수정 | `core/domain/.../port/SummonerRepositoryPort.java` | `findAllByPuuidIn()` 추가 (+4줄) |
| 수정 | `infra/persistence/.../SummonerJpaRepository.java` | `findAllByPuuidIn()` 추가 (+5줄) |
| 수정 | `infra/persistence/.../SummonerRepositoryImpl.java` | 배치 조회 구현 (+11줄) |
| 수정 | `infra/persistence/.../SummonerRankingCalculationService.java` | 핵심 로직 재작성 (+200/-117줄) |
| 수정 | `infra/persistence/.../SummonerRankingScheduler.java` | FLEX 큐 제거 (-1줄) |

---

## 8. 검증 방법

1. **빌드**: `./gradlew build -x test` → BUILD SUCCESSFUL 확인 완료
2. **수동 테스트**: `POST /api/admin/ranking/calculate` 엔드포인트로 랭킹 계산 트리거
3. **로그 검증 항목**:
   - 챌린저/그랜드마스터 API 호출 성공 및 인원수
   - 미등록 소환사 수 및 조회 진행 상황
   - 최종 처리된 랭킹 수
4. **DB 검증 항목**:
   - `summoner_ranking` 테이블: `platform_id='KR'`, `queue='RANKED_SOLO_5x5'` 데이터만 존재
   - `tier_cutoff` 테이블: CHALLENGER/GRANDMASTER KR 데이터 존재
   - `rank_change` 값 정상 계산 여부
