# module:domain:summoner

소환사 데이터 수집, 공개 소환사 조회 API `/api/v1/summoners/**`, lol-server duo 용 내부 조회 API. `league` 의 port·모델에 의존하고 `module:common`(error)·`module:shared`(Platform·Queue)를 사용.

## 책임 / 구조

`com.mmrtr.lol.domain.summoner` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제). `vo/RiotId` 는 경로의 `gameName-tagLine`
- `application/port/` — Outbound Port (Repository/Api/Publish, 조회 전용 `SummonerQueryPort`·`DuoProfileQueryPort`, Riot 조회 락 `SummonerLookupLockPort`)
- `application/usecase/` — UseCase (Inbound Port)
- `readmodel/` — 조회 응답 모델. 공개 API 모델(`Summoner*ReadModel`)은 **lol-server 응답과 필드가 같아야 한다** (`SummonerQueryControllerTest` 가 검증)

## 공개 API — 소환사 조회 (MP-134)

lol-server summoner 모듈의 조회·갱신을 옮겼다 (공개 API 재설계 3.1절). 관전(`/{puuid}/active-game`)은 spectator 컨텍스트.

- `GET /{puuid}` — 저장된 소환사만, 없으면 `NOT_FOUND_PUUID` 404
- `GET /by-riot-id/{gameName-tagLine}?platform=` — `search_name` 으로 찾아 하나로 특정되면 그대로, 아니면 Riot 에서 찾아 저장한다(갱신 일시는 과거로 초기화). Riot 조회는 `SummonerLookupLockPort`(Redis `summoner:lock:lookup:*`, 5초 대기) 안에서, 기다린 요청은 저장된 결과를 다시 찾는다. 태그가 없으면 저장된 소환사만 찾는다
- `GET /autocomplete?q=&platform=` — 랭크 기록이 있는 소환사, `search_name` 접두사, 최대 20건(이름순). 솔로 랭크 티어 우선. 빈 검색어는 빈 목록
- `GET /{puuid}/leagues` — 현재 랭크와 이력(최신순). `oow` 는 현재 백분율 `"50.00"`, 이력 비율 `"0.50"` (lol-server 와 같다)
- `POST /{puuid}/renewal` — 진행 중이면 SUCCESS, 클릭 쿨다운(10초, SET NX)·마지막 Riot 호출 2분 이내면 FAILED(200), 아니면 진행 표시(2분) 후 `mmrtr.exchange` 로 발행하고 SUCCESS(202). 메시지의 플랫폼은 이름(KR, NA …) — 컨슈머가 이름으로 읽는다
- `GET /{puuid}/renewal` — 진행 표시가 있으면 PROGRESS, 없으면 SUCCESS. 진행 표시는 갱신 컨슈머가 끝날 때 지운다
- 갱신 Redis 키(`summoner:renewal:*`·`summoner:click-cooldown:*`)는 lol-server 와 같은 키·값·TTL 이라 공유한다
- `platform` 은 이름(kr, na …)으로 받아 저장 값(KR, NA1 …)으로 바꾼다. 모르는 값은 400 `INVALID_INPUT`

## 내부 API — duo 프로필 (MP-142)

- `GET /internal/v1/summoners/{puuid}/duo-profile` (내부 포트 전용) — lol-server duo `RiotAccountResolver` 의 4회 조회를 대체. 응답 JSON 은 lol-server `RiotAccountStats` 와 같다
- 솔로 랭크 기준: 티어(없으면 `UNRANKED`/`I`/0) · 판 수 상위 3 챔피언(전 시즌, challenges 적재된 게임만) · 최근 20게임(게임 종료 시각 최신순)
- 색인된 적 없는 소환사(`summoner` 행 없음)는 `NOT_FOUND_PUUID` 404 — lol-server 가 `SUMMONER_SEARCH_REQUIRED` 로 안내
- 계약: `docs/api/internal-v1.openapi.yaml` (`InternalApiContractTest` 가 생성 결과와 비교)
- `DuoProfileQueryPort` 는 소환사·리그·매치 테이블을 직접 읽는다 (summoner → match 모듈 의존 없음)

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: persistence(`SummonerQueryPersistenceAdapter`·`DuoProfileQueryPersistenceAdapter` 포함)·redis(`SummonerLookupLockRedisAdapter`·`SummonerRenewalStateRedisAdapter`)·rabbitmq(발행은 league 의 `SummonerCollectionPublishPort`, 갱신 실행은 `SummonerRenewalService`)·riot-client(SummonerApiAdapter)·api(`controller/summoner/SummonerQueryController`·`SummonerRenewalController`, `controller/internal/`).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 검증: `./gradlew :module:domain:summoner:test` (ArchUnit) / `./gradlew :module:domain:summoner:compileJava`.
