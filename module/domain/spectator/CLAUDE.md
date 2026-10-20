# module:domain:spectator

활성 게임(관전) 조회와 공개 API `GET /api/v1/summoners/{puuid}/active-game`. sink 컨텍스트 — `module:common`(error)·`module:shared`(Platform)만 사용.

## 책임 / 구조

`com.mmrtr.lol.domain.spectator` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제). `SpectatorTarget` 은 관전 대상 소환사(저장된 platform_id·revisionDate)
- `application/port/` — Outbound Port (`SpectatorApiPort`, 관전 대상 `SpectatorTargetPort`, 캐시 `ActiveGameCachePort`)
- `application/usecase/` — UseCase (Inbound Port)
- `readmodel/` — 공개 조회 응답 모델. `ActiveGameReadModel` 은 **lol-server `CurrentGameInfoReadModel` 과 필드가 같아야 한다** (`ActiveGameControllerTest` 가 검증)

## 공개 API — 진행 중인 게임 (MP-134)

lol-server `SpectatorService`·`SpectatorFinder` 를 옮겼다.

- 저장된 소환사만 (`NOT_FOUND_PUUID` 404), 저장된 platform_id 를 모르면 `UNKNOWN_SUMMONER_PLATFORM` 404
- 게임 캐시(참여자 전원 puuid 키, 30분) → 게임 없음 캐시(30초) → Riot. 캐시된 게임 시작 뒤 소환사가 갱신됐으면 참여자 캐시를 지우고 다시 찾는다
- 게임 중이 아니면 200 + `data: null`. Riot 조회 실패도 같게 응답하되 캐싱하지 않는다
- 캐시 키 `spectator:v1:*` — lol-server 의 `spectator:*` 와 형식이 달라 공유하지 않는다
- summoner 테이블은 `SpectatorTargetPort` 로 읽는다 (spectator → summoner 모듈 의존 없음)

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: riot-client(SpectatorApiAdapter)·persistence(SpectatorTargetPersistenceAdapter)·redis(ActiveGameCacheRedisAdapter)·api(`controller/spectator/ActiveGameController`, 레거시 `SpectatorController`).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 검증: `./gradlew :module:domain:spectator:test` (ArchUnit) / `./gradlew :module:domain:spectator:compileJava`.
