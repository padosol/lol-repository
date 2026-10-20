# module:domain:match

매치/타임라인 데이터 수집과 공개 매치 조회 API `/api/v1/matches/**`. `readmodel`(Riot MatchDto/TimelineDto, 공개 API 응답 모델)을 가지며 `league` 의 port·모델에 의존하고 `module:common`(error)·`module:shared`(Tier·Division)를 사용.

## 책임 / 구조

`com.mmrtr.lol.domain.match` 하위:
- `domain/` — 도메인 모델 (인프라 무지 — `ArchitectureTest` 강제)
- `application/port/` — Outbound Port (Repository/Api/Publish, 조회 전용 `MatchQueryPort`)
- `application/usecase/` — UseCase (Inbound Port)
- `readmodel/` — Riot 응답 DTO(`*Dto`)와 공개 API 응답 모델(`GameReadModel`·`ParticipantReadModel`·`MatchTimelineReadModel`). 공개 API 모델은 **lol-server 응답과 필드가 같아야 한다** (`MatchControllerTest` 가 검증)

## 공개 API — 매치 조회 (MP-135)

lol-server match 모듈의 조회를 옮긴다 (공개 API 재설계 3.2절).

- `GET /{matchId}` — 저장된 매치만, 없으면 `NOT_FOUND_MATCH_ID` 404. 참가자는 참가자 번호순(아레나 1700·1710 은 순위순), 이름은 저장된 소환사의 현재 Riot ID 우선. 팀 정보는 첫 팀원의 승패·오브젝트 집계 + 팀원 프레임 골드 합
- `GET /{matchId}/timeline` — 참가자별 아이템 구매·스킬 레벨업 순서. 분은 이벤트가 속한 **프레임 시각**으로 센다 (lol-server DB 경로와 같다). 없으면 빈 응답(404 아님)
- 저장된 매치의 참가자 `kda`·`teamDamagePercentage`·`goldPerMinute`·`killParticipation` 은 0 — lol-server DB 조회도 읽지 않는다
- 옛 경로 `/api/v1/match/timeline/{matchId}` 는 옮기지 않는다

## 어댑터 위치

이 컨텍스트의 port 를 구현/소비하는 어댑터는 `module:infra:*` 에 있다: persistence(MatchRepositoryImpl 등, 조회는 `MatchQueryPersistenceAdapter`)·redis(캐시)·rabbitmq·riot-client(MatchApiAdapter)·api(`controller/match/MatchController`).

## 규칙

- 도메인은 인프라 타입(@Entity·RestClient·RedisTemplate·AMQP)을 import 하지 않는다.
- 타 컨텍스트 참조는 그 컨텍스트의 `application`(port·usecase)·도메인 모델만 사용한다.
- 검증: `./gradlew :module:domain:match:test` (ArchUnit) / `./gradlew :module:domain:match:compileJava`.
