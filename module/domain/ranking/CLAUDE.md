# module:domain:ranking

랭킹/티어컷오프 데이터 수집·계산과 공개 조회 API `/api/v1/rankings/**`. 랭킹 계산(`SummonerRankingCalculationService`)·커트라인 시간대 트리거(`SummonerRankingTriggerService`)는 ranking application 에 있고, summoner(`SummonerLookupUseCase`)·match(`MostChampionQueryUseCase`) 의 UseCase 만 부른다.
리더보드·티어 컷오프 조회는 lol-server `leaderboard`·`gamedata`(TierCutoff) 에서 이전했다 (MP-133).

## 책임 / 구조

`com.mmrtr.lol.domain.ranking` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제)
- `readmodel/` — 공개 조회 응답 모델. **lol-server 응답과 필드가 같아야 한다** (`RankingControllerTest` 가 검증)
- `application/port/` — Outbound Port (Repository/Api/Publish). 공개 조회는 쓰기 port 와 나눈 `RankingQueryPort`
- `application/usecase/` — UseCase (Inbound Port)

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: persistence(RankingQueryPersistenceAdapter 등)·rabbitmq·riot-client(LeagueApiAdapter)·api(RankingController).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 공개 조회의 `platform` 은 이름(kr, na …)으로 받아 저장 값(KR, NA1 …)으로 바꾼다. 모르는 platform·page 1 미만은 400 `INVALID_INPUT`.
- 검증: `./gradlew :module:domain:ranking:test` (ArchUnit) / `./gradlew :module:domain:ranking:compileJava`.
