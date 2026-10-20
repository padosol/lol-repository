# module:domain:summoner

소환사 데이터 수집과 lol-server duo 용 내부 조회 API. `league` 의 port·모델에 의존하고 `module:common`(error)·`module:shared`(Queue)를 사용.

## 책임 / 구조

`com.mmrtr.lol.domain.summoner` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제)
- `application/port/` — Outbound Port (Repository/Api/Publish, 조회 전용 `DuoProfileQueryPort`)
- `application/usecase/` — UseCase (Inbound Port)
- `readmodel/` — 조회 응답 모델 (`DuoProfileReadModel`)

## 내부 API — duo 프로필 (MP-142)

- `GET /internal/v1/summoners/{puuid}/duo-profile` (내부 포트 전용) — lol-server duo `RiotAccountResolver` 의 4회 조회를 대체. 응답 JSON 은 lol-server `RiotAccountStats` 와 같다. Riot ID(`gameName`·`tagLine`)는 lol-server 가 매칭 상대 이름으로 스냅샷한다 (lol-server 는 summoner 를 읽지 않는다)
- 솔로 랭크 기준: 티어(없으면 `UNRANKED`/`I`/0) · 판 수 상위 3 챔피언(전 시즌, challenges 적재된 게임만) · 최근 20게임(게임 종료 시각 최신순)
- 색인된 적 없는 소환사(`summoner` 행 없음)는 `NOT_FOUND_PUUID` 404 — lol-server 가 `SUMMONER_SEARCH_REQUIRED` 로 안내
- 계약: `docs/api/internal-v1.openapi.yaml` (`InternalApiContractTest` 가 생성 결과와 비교)
- `DuoProfileQueryPort` 는 소환사·리그·매치 테이블을 직접 읽는다 (summoner → match 모듈 의존 없음)

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: persistence(`DuoProfileQueryPersistenceAdapter` 포함)·rabbitmq·riot-client(SummonerApiAdapter)·api(컨트롤러, `controller/internal/`).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 검증: `./gradlew :module:domain:summoner:test` (ArchUnit) / `./gradlew :module:domain:summoner:compileJava`.
