# module:domain:gamedata

정적·준정적 게임 데이터(패치 버전·시즌·패치노트·큐) 조회. 공개 API `/api/v1/game-data/**` 의 도메인.
lol-server `gamedata` 모듈에서 이전했다 (MP-131). 챔피언 로테이션은 `module:domain:champion` 소관.

## 책임 / 구조

`com.mmrtr.lol.domain.gamedata` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제)
- `readmodel/` — 조회 응답 모델. **lol-server 응답과 필드가 같아야 한다** (`GameDataControllerTest` 가 검증)
- `application/port/` — Outbound Port (Persistence/Cache)
- `application/usecase/` — UseCase (Inbound Port)

## 어댑터 위치

- persistence: `module:infra:persistence` 의 `version`·`queue_type` 패키지
- cache: `module:infra:redis` 의 `GameDataCacheRedisAdapter` (키 `gamedata:*`)
- web: `module:infra:api` 의 `GameDataController`

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 없는 버전·시즌 단건 조회는 lol-server 와 같이 `data: null` 200 (패치노트만 404).
- 검증: `./gradlew :module:domain:gamedata:test` (ArchUnit).
