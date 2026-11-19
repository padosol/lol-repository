# league 재절단 — 헥사고날 1단계

> 표시: **[결정]** 사용자가 정한 것, **[판단]** 설계자가 정한 것(사용자 확인 전). 판단은 근거와 함께 적는다.
> 기준 코드: develop `97d0ae1`.

## 1. 왜 하나

- 사용자 요청: 이 레포를 헥사고날 아키텍처로 옮긴다. 첫 단계로 컨텍스트 경계를 데이터 소유에 맞추고, 잘못 놓인 로직을 고친다. **[결정]**
- 범위는 "A′"로 정했다. 경계 재절단, 오배치 수정, ArchUnit 가드까지만 하고, 어댑터는 지금 위치(`module/infra/*`)에 둔다. **[결정]**
- 지금 경계는 실제 데이터 소유와 맞지 않는다. 문제는 다섯 가지다.
  1. **league 테이블 쓰기:** league 컨텍스트는 자기 이름의 테이블에 쓰지 않는다. `league`·`league_summoner`(+history)에 쓰는 곳은 summoner의 `SaveSummonerDataUseCase` 하나뿐이다(`:39` `saveIfAbsent`, `:60` `save`). league 모듈이 실제로 쓰는 테이블은 `summoner_ranking`·`tier_cutoff`(+backup)뿐이다.
  2. **랭킹 계산 위치:** 랭킹 계산 로직이 `infra:persistence`의 `SummonerRankingCalculationService`(319줄)에 있다. 이 클래스는 summoner(`SummonerService`, `SummonerRepositoryPort`)와 match(`MatchSummonerJpaRepository`)를 직접 쓴다. 그대로 league application으로 옮기면 league↔summoner Gradle 순환이 생긴다.
  3. **평균 티어 규칙 위치:** 평균 티어 규칙이 persistence의 `MatchService.saveAll`(`:71-115`)에 있다.
  4. **갱신 단계의 Riot 호출:** 갱신 단계 셋이 포트 없이 `RiotApiService`와 Riot DTO를 직접 쓴다. 위치는 `RenewalAccountStep:78`, `RenewalLeagueStep:81`, `RenewalSummonerStep:135`다.
  5. **스케줄러가 UseCase 구현:** `SummonerRankingScheduler`(persistence)가 `TriggerSummonerRankingCalculationUseCase`를 구현한다. "티어 커트라인은 KST 00:00~02:00에만 갱신" 규칙도 이 클래스에 들어 있다.
- 경계를 지키는 장치도 부족하다. 지금은 "도메인 모듈이 인프라 타입을 import하지 않는다" 규칙만 있다. persistence 안에서 컨텍스트끼리 import하는 것은 막지 않는다.

## 2. 목표 · 비목표 · 안티목표

### 목표
- **league 재절단:** league 테이블의 도메인 타입과 포트를 summoner로 옮기고, 남은 league를 `ranking`으로 바꾼다. 모듈 의존은 match→summoner, ranking→summoner·match가 된다. 순환은 없다. **[결정]**
- **오배치 4건 수정:** 1장의 2·3·4·5번이다. **[결정]**
- **ArchUnit 가드:** 컨텍스트끼리는 port.in·domain·readmodel로만 의존하게 한다. persistence 안의 컨텍스트 교차 import는 지금 상태를 기준선으로 얼린다. infra가 UseCase를 구현하지 못하게 한다. **[결정]**
- **동작은 바꾸지 않는다.** 다음이 모두 지금과 같아야 한다. **[판단]** 구조만 바꾸는 작업이라서다.
  - 공개·내부 API 응답(`InternalApiContractTest`, RestDocs 테스트)
  - Redis 키와 `MatchCacheView` 형태
  - 테이블·컬럼(마이그레이션 없음)
  - 평균 티어 값
  - Riot 호출 횟수, `RiotMethod` 비용, `RiotCallPriority`

### 비목표
- 다음은 다음 스펙에서 다룬다. **[결정]**
  - 어댑터를 컨텍스트 모듈 안으로 옮기기
  - `support/*` 모듈 분리
  - `riot-dto` 모듈 추출
  - `module/infra/*` 삭제
  - `export` 모듈 분리
- 배경 매치 적재의 ack 정책. Linear MP-187로 따로 다룬다. **[결정]**
- persistence 교차 조회 두 곳의 정리. **[판단]** 어댑터가 한 모듈(`infra:persistence`)에 있는 동안에는 Gradle 경계 문제가 아니다. 어댑터를 옮길 때 같이 정리하는 편이 덜 바꾼다. 지금은 기준선에 기록만 한다(5.6).
  - `DuoProfileQueryPersistenceAdapter`가 match 테이블을 읽는다.
  - `SpectatorTargetPersistenceAdapter`가 `SummonerJpaRepository`를 쓴다. 이것은 `SpectatorTargetPort` 주석에 적힌 대로 의도한 결정이다.
- rabbitmq가 `RenewalMatchClaimService`·`MatchRedisService` 구체 클래스를 직접 쓰는 것. **[판단]** 다음 스펙에서 다룬다.
- 갱신 단계를 summoner·match application으로 옮기기. **[판단]** 어댑터 이동과 함께 한다. 이번에는 Riot 호출만 포트로 바꾼다.
- `MatchService`가 challenges가 없는 참가자의 participant 행을 저장하지 않는 동작. **[판단]** 그대로 둔다. 바꾸면 저장 결과가 달라진다.

### 안티목표
- **평균 티어 값이 한 건이라도 달라지는 것.** #146의 `tier_bucket` 생성 컬럼이 `average_tier`에 의존한다.
- **갱신 파이프라인 동작이 바뀌는 것.** 리스너 독립, 사용자 응답 5초, 429 인라인 재시도 없음, permit 비용, 실패 분류(`RiotFailures`)가 모두 유지돼야 한다.
- **새 의존 문제가 생기는 것.** 중간 PR에서도 Gradle 순환이나 컨텍스트 간 port.out 의존이 새로 생기면 안 된다.
- **테이블 이름·마이그레이션을 바꾸는 것.**

## 3. 접근안

| 안 | 내용 | 판단 |
|---|---|---|
| **A′ (선택)** | 재절단 + 오배치 4건 + ArchUnit 가드. 어댑터 위치는 유지한다 | **[결정]** 검토자 판단으로 얻을 것의 대부분이 이 범위에서 나온다. 단계마다 독립된 PR이 된다. 최근 가장 자주 바뀌는 rabbitmq 쪽과 #146을 거의 건드리지 않는다 |
| A | A′ + `riot-dto` 추출 | riot-client가 어댑터로 남아 있는 동안에는 필요 없다. riot-client를 도메인 모르는 공용 모듈로 바꿀 때(다음 스펙) 정한다 |
| B | 최종 구조까지 9단계 | L 크기 단계가 두 개다. 갱신 파이프라인 작업, #146과 충돌한다 |
| C | 재절단 없이 오배치만 | 랭킹 계산을 league application으로 옮기는 순간 league↔summoner 순환이 생긴다 |

다음 두 안은 두 차례 검토에서 코드 근거로 기각했다.
- **league를 sink로 두고 `renewal` 모듈을 따로 만드는 원안:** 갱신 단계가 summoner·match의 port.out을 직접 쓴다. port.in만으로는 성립하지 않는다.
- **summoner·league·match 병합:** 순환은 잘못된 경계 때문이었고, 재절단으로 없어진다. 병합하면 그 경계를 숨기기만 한다.

## 4. 이 스펙을 마친 뒤의 모습

```
module/
├── app/application · shared · common · support/logging      (그대로)
├── domain/
│   ├── summoner                 리그 테이블을 소유
│   │   ├── domain/              Summoner · vo · Renewal*  + League · LeagueSummoner
│   │   └── application/
│   │       ├── port/            + LeagueRepositoryPort · LeagueSummonerRepositoryPort · SummonerRenewalPublishPort
│   │       │                    SummonerApiPort 에 단계별 조회 메서드 추가
│   │       └── usecase/         + 리그 조회 입구 (match 가 씀) · 소환사 조회 입구 (ranking 이 씀)
│   ├── match                    → summoner
│   │   ├── domain/              + MatchTierSnapshot  (신규 패키지)
│   │   └── application/         + 모스트 챔피언 조회 입구 (ranking 이 씀)
│   ├── ranking                  domain:league 에서 이름 변경, → summoner · match
│   │   ├── domain/              SummonerRanking · TierCutoff · RankingQueue
│   │   └── application/         RankingQueryService · TierCollectionService · 랭킹 계산 · 트리거
│   │                            port 에 TierCollectionPublishPort (발행 포트에서 분리)
│   └── champion · gamedata · spectator · championstats      (그대로)
└── infra/                       위치 그대로
    ├── persistence              league/ → summoner/league/ 와 ranking/ 으로 나눔
    ├── rabbitmq                 갱신 단계가 SummonerApiPort 를 씀
    └── api · redis · riot-client · bigquery                 (import 경로만 바뀜)
```

**컨텍스트 간 의존 규칙 [판단]:**
- 다른 컨텍스트에서 쓸 수 있는 패키지는 `application.usecase..`(port.in), `domain..`, `readmodel..` 세 가지뿐이다.
- 쓸 수 없는 것은 `application.port..`(port.out)와 `application` 바로 아래의 서비스 클래스다.
- 근거: port.out을 다른 컨텍스트가 쓰면, 그 테이블을 누가 소유하는지가 다시 흐려진다.

## 5. 설계 상세

### 5.1 league 재절단

**summoner로 옮기는 것 [판단: 패키지]**
- `League`, `LeagueSummoner` → `com.mmrtr.lol.domain.summoner.domain` (`Summoner`와 같은 패키지)
- `LeagueRepositoryPort`, `LeagueSummonerRepositoryPort` → `com.mmrtr.lol.domain.summoner.application.port`
- 위 클래스들과 `SummonerCollectionPublishPort`를 import하는 파일은 모두 17개다.

**발행 포트를 둘로 나눈다 [판단]**
- `SummonerCollectionPublishPort`의 두 메서드는 쓰는 쪽과 보내는 큐가 서로 다르다.

  | 메서드 | 쓰는 곳 | 보내는 곳 |
  |---|---|---|
  | `publishForRenewal` | summoner `SummonerRenewalRequestService:62` | summoner 큐, USER 우선순위 |
  | `publishForCollection` | ranking `TierCollectionService:76` | 매치 찾기 큐 `RENEWAL_MATCH_FIND`, BACKGROUND 우선순위 |

- 포트를 통째로 summoner로 옮기면 ranking이 summoner의 port.out에 의존하게 된다. 이것은 4장 규칙과 2장 안티목표에 걸린다.
- 그래서 둘로 나눈다.
  - summoner `SummonerRenewalPublishPort.publishForRenewal`
  - ranking `TierCollectionPublishPort.publishForCollection`
- rabbitmq의 `SummonerCollectionPublishAdapter`가 두 포트를 모두 구현한다. 메시지와 큐는 그대로다.
- 근거: 발행하는 쪽이 자기 포트를 갖게 되므로 새 의존이 생기지 않는다. ranking에 남는 파일 중 옮겨 가는 타입을 쓰는 곳은 `TierCollectionService`(와 테스트) 하나뿐이고, 그 이유가 이 포트다(확인함).
- 다른 방법(summoner나 match에 이 발행을 감싸는 입구 만들기, `TierCollectionService`를 다른 컨텍스트로 옮기기)은 이번에 하지 않는다. 입구는 port.out을 그대로 넘기기만 한다. 서비스를 옮기는 것은 경계 문제라 9장에 남긴다.

**나머지 league를 ranking으로 [판단]**
- 모듈은 `module:domain:league` → `module:domain:ranking`, 패키지는 `com.mmrtr.lol.domain.league` → `com.mmrtr.lol.domain.ranking`으로 바꾼다.
  - 근거: 리그 테이블을 소유하지 않는 모듈에 `league`라는 이름이 남으면 이번에 바로잡는 혼동이 다시 생긴다.
- 옮기는 대상:
  - domain: `SummonerRanking`, `TierCutoff`, `RankingQueue`
  - port: `LeagueApiPort`, `RankingQueryPort`, `SummonerRankingRepositoryPort`, `TierCutoffRepositoryPort`
  - application: `RankingQueryService`, `TierCollectionService`
  - usecase: `CollectTierDataUseCase`, `RankingQueryUseCase`, `TriggerSummonerRankingCalculationUseCase`
  - readmodel: 2개
  - `ArchitectureTest`, `CLAUDE.md`
- `LeagueApiPort`는 이름을 유지한다. Riot의 league API(apex·티어별 엔트리)를 부르는 포트라서 이름이 맞다. 쓰는 곳은 랭킹 계산과 `TierCollectionService`뿐이다.
- **[판단] 호출하는 곳이 없는 `CalculateSummonerRankingUseCase`·`SaveTierCutoffUseCase`는 지운다.** 자기 파일 말고는 참조가 없다(확인함). 옮겨 봐야 쓰이지 않는 코드다.

**persistence 패키지 나누기 [판단]**
- 리그 테이블: `LeagueEntity`, `LeagueSummonerEntity`, `LeagueSummonerHistoryEntity`와 각 `*JpaRepository`, `LeagueRepositoryImpl`, `LeagueSummonerRepositoryImpl` → `infra.persistence.summoner.league.{entity,repository}`
- 랭킹: `SummonerRanking*`, `TierCutoff*`, `RankingQueryPersistenceAdapter`, `SummonerRankingProjection`, `scheduler/` → `infra.persistence.ranking.*`
- `MostChampionProjection` → `infra.persistence.match.repository`. 이 projection을 반환하는 것이 `MatchSummonerJpaRepository`이기 때문이다.
- 엔티티 이름은 바꾸지 않는다. 그래서 JPQL 문자열(`JOIN SummonerEntity`, `FROM LeagueSummonerEntity`)과 테이블이 그대로이고, 마이그레이션이 필요 없다.

**Gradle**
- `settings.gradle`
- 루트 `build.gradle`의 `archTest` 목록: `league` → `ranking`
- `module:domain:match`: `domain:league` → `domain:summoner`
- `module:domain:summoner`: `domain:league` 의존 제거
- `module:domain:ranking`: `domain:summoner`, `domain:match` 추가
- infra 모듈: `domain:league` → `domain:ranking`. 리그 타입만 쓰는 곳은 `domain:summoner`로 바꾼다.

### 5.2 컨텍스트 간 입구(port.in) 추가

이름은 **[판단]**이고, 빌더가 바꿔도 된다.

| 쓰는 쪽 | 지금 직접 쓰는 것 | 새 입구 |
|---|---|---|
| match `SaveMatchDataUseCase` | `LeagueSummonerRepositoryPort.findAllByPuuidsAndQueue` (port.out) | summoner `LeagueSummonerQueryUseCase` |
| ranking 랭킹 계산 | `SummonerRepositoryPort.findAllByPuuidIn` (port.out), `SummonerService.getSummonerByPuuid` (서비스 클래스) | summoner `SummonerLookupUseCase`. 두 메서드를 담고, `SummonerService`가 구현한다 |
| ranking 랭킹 계산 | `MatchSummonerJpaRepository.findMostChampionsByPuuids` (match 어댑터) | match `MostChampionQueryUseCase` → match port.out → persistence 구현 |

- 모스트 챔피언 결과는 match `readmodel`의 record(puuid, championName, playCount)로 돌려준다. persistence의 projection은 밖으로 내보내지 않는다.
- `SummonerService.getSummonerByPuuid`는 Riot을 부르고 DB에 저장한다. 입구로 감싸도 동작·우선순위(`RiotCallPriority.BACKGROUND`)·permit 사용은 같아야 한다.

### 5.3 랭킹 계산을 ranking application으로

- `SummonerRankingCalculationService`의 로직을 `com.mmrtr.lol.domain.ranking.application`으로 옮긴다.
- 의존을 5.2의 입구로 바꾸는 것 말고는 로직을 바꾸지 않는다. 그대로 유지할 것: 백업 → apex 조회 → 랭킹 교체 → 커트라인 교체 순서, 오류 시 백업 정리, permit 획득.
- `TriggerSummonerRankingCalculationUseCase`는 ranking application이 구현한다. 커트라인 시간대 규칙(KST 00:00~02:00)과 큐 목록(`RANKED_SOLO_5x5`)은 지금 스케줄러에서 이리로 옮긴다.
- **[판단] 스케줄러:** `SummonerRankingSchedule`(`@Scheduled(fixedDelay = 7200000, initialDelay = 60000)`)을 driving adapter로 남겨 트리거 UseCase를 호출하게 한다. `SummonerRankingScheduler`는 없앤다. 위치는 `infra.persistence.ranking.scheduler`에 둔다. 어댑터 이동은 다음 스펙에서 한다.
- `AdminRankingController`는 지금처럼 트리거 UseCase를 부른다. 바뀌는 것은 구현체뿐이다.
- 테스트는 로직과 함께 옮긴다. `SummonerRankingCalculationServiceTest`, `SummonerRankingSchedulerTest`(시간대 규칙)가 해당하고, `SummonerRankingScheduleTest`는 호출만 확인하도록 남긴다.

### 5.4 평균 티어를 match로

- `com.mmrtr.lol.domain.match.domain.MatchTierSnapshot`을 새로 만든다. match에는 아직 `domain` 패키지가 없다.
  - 입력: 한 매치의 참가자 puuid 목록, 그 매치 큐의 `puuid → LeagueSummoner`
  - 출력: 참가자별 (tier, rank, absolutePoints), `averageTier`(Integer, 없으면 null)
- 규칙은 지금 코드와 **똑같이** 둔다.
  - 리그 정보가 있는 참가자의 `absolutePoints`만 모은다.
  - `(int)`로 `double` 평균을 자른다.
  - 모인 값이 없거나 큐를 알 수 없으면(`Queue.fromQueueId == null`) `averageTier`는 null이다.
  - challenges가 없는 참가자도 평균에 들어간다. 지금 코드가 그렇다.
- `SaveMatchDataUseCase`가 매치마다 스냅샷을 만든다. 포트는 `MatchRepositoryPort.saveAll(matches, timelines, Map<String, MatchTierSnapshot> byMatchId)`가 된다. **[판단]**
- `MatchService`는 받은 값을 엔티티에 그대로 쓰기만 한다. challenges가 없는 참가자를 저장하지 않는 동작은 유지한다.
- 같은 PR에 특성 테스트를 둔다. 확인할 경우:
  - 리그 정보가 일부 참가자에게만 있는 경우
  - challenges가 없는 참가자가 있는 경우
  - 큐를 알 수 없는 경우
  - 리그 정보가 아무에게도 없는 경우
  - 소수점을 자르는 경우(예: 1과 2 → 1)

### 5.5 갱신 단계의 Riot 호출을 포트로

- `SummonerApiPort`에 단계별 조회 메서드 세 개를 더한다. 각 메서드는 지금처럼 `RiotPermit` 하나와 `Executor`를 받는다. **[판단]**
  - 계정 조회: gameName·tagLine
  - 소환사 조회: profileIconId·summonerLevel·revisionDate
  - 리그 엔트리 조회: `Set<LeagueInfo>`
- 지금 있는 두 메서드(`fetchSummonerByRiotId`, `fetchSummonerByPuuid`)는 그대로 둔다. 이 둘은 세 호출을 묶은 것이다.
- 구현은 `SummonerApiAdapter`(riot-client)가 `RiotApiService`로 한다. `RenewalLeagueStep.toLeagueInfos`의 DTO 변환은 어댑터로 옮긴다.
- **예외는 감싸지 않는다.** 갱신 실패 분류(`RiotFailures.isTransient`/`isClientError`, `RenewalStepFailures`의 `RiotClientNotFoundException`)는 cause 사슬에 있는 riot-client 예외 타입을 본다.
- 갱신 단계는 나머지를 그대로 둔다: permit 요청(`RiotMethod`·비용·`RiotCallPriority.USER`), 재시도·재발행, `summonerRepositoryPort` 사용. 바뀌는 것은 Riot 호출 한 줄과 DTO 타입뿐이다.
- 기존 테스트(`RenewalAccountStepTest`, `RenewalSummonerStepTest`, `RenewalLeagueStepTest`)가 그대로 통과해야 한다. 고쳐도 되는 것은 mock 대상(`RiotApiService` → `SummonerApiPort`)뿐이다.

### 5.6 ArchUnit 가드

**컨텍스트 모듈마다 `ArchitectureTest`**
- 대상은 summoner, match, ranking, spectator, champion, gamedata, championstats다.
- 지금 있는 "인프라 타입 금지"는 유지한다.
- 다른 컨텍스트의 `application.port..`와 `application` 바로 아래 서비스 클래스에 의존하지 못하게 하는 규칙을 더한다(4장 규칙).

**app 모듈의 전역 테스트 [판단: app에 두고 `archTest`에 포함]**
- infra와 컨트롤러 클래스(`com.mmrtr.lol.infra..`, `com.mmrtr.lol.controller..`)가 `..application.usecase..` 인터페이스를 구현하지 못하게 한다.
- persistence에서 컨텍스트끼리 import하지 못하게 하고, `FreezingArchRule`로 지금 위반을 기준선으로 얼린다.
  - 슬라이스는 패키지가 아니라 **컨텍스트 단위**로 정의한다. 예를 들어 gamedata = `version` + `queue_type`, summoner = `summoner` (리그 포함)이다. `config`는 제외한다.
  - 재절단 뒤 기준선에 남을 것으로 예상하는 위반: `DuoProfileQueryPersistenceAdapter` → match, `SpectatorTargetPersistenceAdapter` → summoner
- freeze 저장소는 `module/app/application/src/test/resources/archunit_store`에 둔다. CI에서 새 위반이 생기면 실패해야 한다.

**ArchUnit이 못 보는 문자열 조인은 목록으로 남긴다**
- `MatchQueryPersistenceAdapter`의 `LEFT JOIN summoner` (match → summoner 방향이라 허용)
- 재절단 뒤 summoner 내부가 되는 두 곳: `LeagueSummonerJpaRepository`의 `JOIN SummonerEntity`, `SummonerJpaRepository`의 `EXISTS … LeagueSummonerEntity`

### 5.7 문서

- 루트 `CLAUDE.md`(모듈 표), `docs/ARCHITECTURE.md`(의존 그래프·영향표), `module/domain/{league→ranking, summoner, match}/CLAUDE.md`를 고친다.
- #146도 루트·match `CLAUDE.md`를 고치므로, 먼저 머지되는 쪽에 맞춰 충돌을 푼다.

## 6. PR 나누기

PR마다 `./gradlew check`를 통과해야 하고, 동작은 바뀌지 않아야 한다. **[판단] 순서**는 서로 독립인 것을 먼저 하고, 재절단 뒤에 그것에 기대는 것을 한다.

1. **평균 티어 → match** (S~M). `MatchTierSnapshot`, `MatchRepositoryPort` 시그니처, `MatchService`, 특성 테스트.
2. **갱신 단계 Riot 호출 → `SummonerApiPort`** (M). summoner port, riot-client adapter, rabbitmq 단계 3개.
3. **league 재절단** (M, 대부분 기계적). 5.1 전부(발행 포트 나누기 포함), 5.2의 `LeagueSummonerQueryUseCase`, 문서.
4. **랭킹 계산 → ranking application** (M). 5.2의 나머지 입구, 5.3.
5. **ArchUnit 가드** (S). 5.6. 규칙이 최종 패키지 이름을 쓰므로 마지막에 한다.

## 7. 검증

- PR마다 `./gradlew check`(Checkstyle + 전 모듈 테스트, Testcontainers)와 `./gradlew archTest`를 돌린다.
- 동작이 같은지는 다음으로 확인한다.
  - `InternalApiContractTest`, `PublicApiMappingTest`, RestDocs 테스트
  - `RiotSchemaMigrationTest`(엔티티 패키지를 옮겨도 validate가 통과하는지)
  - `ComponentBeanNameTest`(옮긴 뒤 빈 이름이 겹치지 않는지)
  - `MatchServiceSaveOrderTest`
- 평균 티어는 5.4의 특성 테스트로 바뀌기 전과 같은 값을 내는지 확인한다.

## 8. 빌더에게 맡기는 것

- 새 입구·포트의 정확한 이름과 메서드 시그니처. 5.2·5.5의 이름은 제안이다.
- 옮긴 클래스의 테스트 구조, PR 안의 커밋 단위.
- `SummonerLookupUseCase`를 하나로 둘지 둘로 나눌지.

## 9. 다음 스펙의 방향 (이번에 하지 않음)

```
module/
├── app/application      조립 · 공용 실행기 · 전 엔티티 스키마 검증 · 계약 테스트
├── shared · common
├── riot-dto?            Riot 응답 클래스 (만들지 다음 스펙에서 정함)
├── support/             logging · riot-client · redis · amqp · persistence · web   (domain 모름)
├── domain/
│   ├── summoner         domain · application · renewal/ · adapter/{in,out}
│   ├── match            domain · application/{ingest,query} · adapter/{in,out}
│   ├── ranking          domain · application · adapter/{in,out}
│   └── champion · gamedata · spectator · championstats      (각자 adapter 포함)
├── export               backfill · 통계 내보내기
└── infra/*              삭제
```

다음 스펙에서 정할 것:
- `riot-dto`를 만들지
- 갱신 단계를 각 컨텍스트 application으로 옮기기
- `DuoProfile`·`SpectatorTarget` 교차 조회 정리
- 공용 실행기(`AsyncConfig`)의 위치
- rabbitmq의 구체 클래스 결합
- `TierCollectionService`가 있을 곳. 랭킹 테이블을 쓰지 않고, Riot 리그 API로 puuid를 모아 매치 수집을 시작시키기만 한다.
