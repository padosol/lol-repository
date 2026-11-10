# LOL Repository

## 프로젝트 소개

LOL Repository 는 League of Legends 데이터 **수집/저장 전용 컨슈머**입니다.
메인 서버(`lol-server`) 가 발행한 RabbitMQ 메시지를 받아 Riot API 를 호출하고,
정규화된 결과를 PostgreSQL 에 적재합니다.

수집 대상:
- 소환사 (Summoner) 정보 및 갱신 이벤트
- 매치 (Match) 메타 + 타임라인
- 리그/티어 (League) 랭킹
- 챔피언 로테이션 (Champion Rotation)
- 실시간 게임 (Spectator, Active Game)

> **책임 경계** — 검색/조회 API 는 메인 서버(`lol-server`) 책임입니다.
> 본 모듈은 _수집과 저장_ 만 담당하며, REST 컨트롤러는 운영(어드민) 및 일부 캐시 조회용으로 한정됩니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 21 (Toolchain) |
| Framework | Spring Boot 3.5.16 |
| Build | Gradle (Multi-Module) |
| Database | PostgreSQL + JPA/Hibernate (`reWriteBatchedInserts`, `batch_size=1000`) |
| Migration | Flyway — `riot` 스키마 전용 체인 (`module/infra/persistence/src/main/resources/db/migration/riot`) |
| Cache / 분산 락 | Redis + Redisson 3.46.0 |
| Message Queue | RabbitMQ 3.13 |
| Batch | Spring Batch (백필 잡), Google Cloud Storage (NDJSON export) |
| 동시성 | Java Virtual Threads (`LOL_VT_ENABLED=true` 기본) |
| Monitoring | Micrometer + Prometheus, Spring Actuator |
| Code Quality | Checkstyle 10.21.4 (`maxWarnings=0`), JaCoCo 0.8.12 |

## 시스템 아키텍처

```
┌─────────────┐    ┌────────────┐    ┌────────────────┐    ┌──────────┐
│ Main Server │ -> │  RabbitMQ  │ -> │ LOL Repository │ -> │ Riot API │
└─────────────┘    └────────────┘    └────────────────┘    └──────────┘
                                            │
                                            ▼
                                     ┌─────────────┐
                                     │ PostgreSQL  │
                                     └─────────────┘
```

## 모듈 구조

```
lol-repository/
├── app/                    # Spring Boot 진입점 (LolRepositoryApplication)
│   └── backfill/           # Spring Batch 기반 매치 백필 잡 + GCS export
├── core/
│   ├── domain/             # DDD 도메인 (Port + UseCase + Application Service)
│   └── enum/               # 공유 enum (Region, Platform, Tier, Queue, ...)
├── infra/
│   ├── api/                # REST 컨트롤러 (어드민/spectator/champion/summoner 조회)
│   ├── persistence/        # JPA 엔티티 + Repository 어댑터 + Flyway
│   ├── redis/              # Redisson 클라이언트, 캐시, 분산 락
│   ├── rabbitmq/           # 큐 리스너, 발행자, RateLimiter 설정
│   └── riot-client/        # Riot API RestClient + Rate Limit / Retry / 동시성 제어
└── support/                # 공통 예외(CoreException), MDC/AOP, 트레이싱
```

### 도메인 패키지 (`core/domain/com.mmrtr.lol.domain`)

```
domain/
├── summoner/    # 소환사 갱신·수집 유스케이스
├── match/       # 매치 + 타임라인 (readmodel.timeline)
├── league/      # 리그/티어 랭킹
├── champion/    # 챔피언 로테이션
└── spectator/   # 활성 게임 조회
```

각 도메인은 `application/port` (Outbound Port), `application/usecase` (UseCase),
`domain` (모델) 으로 구성된 헥사고날 구조를 따릅니다.

### 모듈 의존성

```
                          ┌─────────────────┐
                          │       app       │
                          └────────┬────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        ▼                          ▼                          ▼
┌───────────────┐         ┌────────────────┐         ┌──────────────────┐
│  infra:api    │         │ infra:rabbitmq │         │ infra:riot-client│
└───────┬───────┘         └────────┬───────┘         └─────────┬────────┘
        │                          │                           │
        └──────────────────────────┼───────────────────────────┘
                                   ▼
        ┌──────────────────────────┬──────────────────────────┐
        ▼                          ▼                          ▼
┌────────────────┐        ┌────────────────┐         ┌────────────────┐
│infra:persistence│        │  infra:redis   │         │  core:domain   │
└────────┬────────┘        └────────┬───────┘         └────────┬───────┘
         │                          │                          │
         └──────────────────────────┼──────────────────────────┘
                                    ▼
                          ┌────────┴────────┐
                          ▼                 ▼
                   ┌────────────┐    ┌───────────┐
                   │ core:enum  │    │  support  │
                   └────────────┘    └───────────┘
```

## 데이터 흐름

```
1. 메인 서버
   └─> RabbitMQ (mmrtr.summoner / renewal.match.find.queue / mmrtr.matchId.priority)

2. SummonerRenewalListener
   └─> Riot API: 소환사·매치 ID 목록 조회
       └─> MatchIdPublisher → mmrtr.matchId.priority 큐로 매치 ID 재발행

3. MatchFindListener  (renewal.match.find.queue)
   └─> Riot API: match + timeline 조회
       └─> MatchBatchProcessor.add(MatchDto, TimelineDto)

4. MatchBatchProcessor
   └─> @Scheduled(fixedRate=1000) flush
       └─> SaveMatchDataUseCase 일괄 저장 (Hibernate jdbc.batch_size=1000)
```

### 큐 정의 (`RabbitMqBinding`)

| 큐 | Exchange | Routing Key | 용도 |
|----|----------|-------------|------|
| `mmrtr.summoner` | `mmrtr.exchange` | `mmrtr.key` | 소환사 수집/갱신 |
| `mmrtr.summoner.dlx` | `summoner.dlx.exchange` | `deadLetter` | 소환사 DLX |
| `mmrtr.matchId.priority` | `mmrtr.matchId.priority.exchange` | `mmrtr.routingkey.matchId` | 매치 적재 (우선순위 큐, `x-max-priority: 3`) |
| `renewal.match.find.queue` | `renewal.topic.exchange` | `renewal.match.find` | 매치 상세 수집 |

## Rate Limiting & 배치 삽입

Riot API 호출 한도는 **호출하는 쪽이 먼저 허가를 받아** 지킨다. `RiotPermitPort.tryAcquire(platform, priority, counts)` 가
routing 마다 Lua `riot-permit.lua` 한 번으로 app·method 규칙을 모두 보고(전부 되거나 전부 안 되거나), 기다리지 않고 승인·거절만 답한다.
카운터는 Redis(ZSET, 키 `riot:rl:*`)에 있어 모든 인스턴스가 같은 예산을 나눠 쓰고, 재시작해도 이어서 센다.
`RiotApiService` 의 모든 메서드와 도메인 포트가 이 허가를 인자로 받는다. 인터셉터 체인(`RetryInterceptor`·`RiotRateLimitInterceptor`)은 없다.
설계는 `docs/superpowers/specs/2026-11-09-riot-permit-check-design.md` (앞선 `2026-11-05-sliding-rate-limiter-design.md` 의 4.4 를 대체) 참고.

1. **호출 우선순위** — 허가를 받을 때 `USER`·`DEFERRED`·`BACKGROUND` 중 하나를 준다.

   | 우선순위 | 호출 | 한도가 찼을 때 |
   |---|---|---|
   | `USER` | 갱신 단계(소환사·계정·리그·매치), 공개 API 동기 조회 | 거절되면 바로 돌려받는다 |
   | `DEFERRED` | 나중 타임라인(`TIMELINE_LATER`) | 유저 활동 중이면 창의 80% 까지만 쓴다 |
   | `BACKGROUND` | 추가 매치 찾기·백필, 티어 수집, 최상위 랭킹 계산 | 늘 초당 40회(`background-max-per-second`), 유저 활동 중이면 창의 50% 까지만 쓴다 |

2. **절반 줄이기** — 묶음(예: 매치 20건)이 거절되면 기다리지 않고 n → ⌈n/2⌉ → ⌈n/4⌉ (20 → 10 → 5) 로 줄여 바로 다시 묻는다.
3. **거절 처리는 호출하는 쪽마다 정한다** —

   | 호출하는 쪽 | 거절되면 |
   |---|---|
   | 갱신 단계 | 1초 뒤 requeue |
   | 매치 적재 큐 | `DENIED` → 1초 자고 requeue |
   | 공개 API — 관전 (`ActiveGameQueryService`, `/api/v1/summoners/{puuid}/active-game`) | 진행 중인 게임 없음으로 응답 (캐시하지 않음) |
   | 공개 API — 챔피언 로테이션 조회 (`ChampionRotationQueryService`, `/api/v1/game-data/champion-rotations`) | 빈 로테이션으로 응답 (60초 캐시) |
   | 그 밖의 공개 API (Riot ID 조회, 이행용 `/api/riot/**` 등) | HTTP 429 `TOO_MANY_REQUESTS` |
   | 스케줄러 | `acquireWithRetry` (1초 간격 10번) |
   | 매치 찾기 | 거절된 offset 부터 requeue |

4. **응답 뒤 기록** — `RiotCallRecorder` 가 응답(또는 IO 예외)을 받으면 기록을 허가 시각에서 응답 시각으로 옮기고(Riot 이 센 시각에 맞춘다),
   응답 헤더(`X-App-Rate-Limit`·`X-Method-Rate-Limit`)에서 한도를 배우며, Count 가 한도의 95% 이상이면 1초,
   429 면 `Retry-After` 동안 해당 버킷을 Redis 키로 정지시킨다(모든 인스턴스가 함께 멈춘다). **어떤 응답도 다시 보내지 않는다.**
   429·5xx·IO·시간 초과는 호출하는 쪽이 메시지 단위로 1초 뒤 시도 수를 올려 다시 넣는다 (갱신 단계 `attempt`, 매치 적재 큐 `x-attempt` 헤더, 매치 찾기 `attempt`). 상한에 닿으면 갱신은 실패, 매치 적재는 DLX, 매치 찾기는 버린다.
5. **갱신 매치 단계** — 사용자 경로는 최근 3게임의 타임라인만 받는다. 나머지는 매치 적재 큐의 `TIMELINE_LATER` 순위(`DEFERRED`, 타임라인만, 배치 저장)로 보내 나중에 채운다.
   최근 3게임 타임라인은 갱신 성공 조건이 아니다.
6. **메트릭** — `riot.permit.requests{routing,priority,result=granted|denied}`(허가 요청 수), `riot.api.responses{status,host}`,
   `riot.api.rate_limited{routing,type}`(429, `X-Rate-Limit-Type` 별), `riot.ratelimit.near_limit{routing,scope}`.

PostgreSQL 적재는 두 단계로 흡수:
- **인-메모리 큐 + 1 초 flush** — `MatchBatchProcessor` 가 매치를 모아 `@Scheduled(fixedRate=1000)` 로 일괄 flush.
- **JDBC 배치** — Hibernate `batch_size=1000`, `order_inserts/updates`, JDBC URL 에 `reWriteBatchedInserts=true`.

## 빌드 & 실행

### 인프라 (Docker Compose)

```bash
docker compose -f docker/infra/compose.yml up -d --wait      # PostgreSQL · RabbitMQ · Redis
docker compose -f docker/monitoring/compose.yml up -d        # Prometheus · Grafana (인프라 다음에)
```

기본 포트: PostgreSQL `5432` (DB `lol_local`), RabbitMQ `5672` (관리 UI `15672`), Redis `6379`, Prometheus `19090`, Grafana `13000`.
모니터링은 호스트에서 뜬 앱(`:8111`)과 RabbitMQ 를 수집한다.

### 부하 테스트 (실제 Riot API)

k6 스크립트와 데이터셋은 `load-test/` 에 있다. 실행 절차는 [load-test/README.md](load-test/README.md) 를 본다.

### 빌드

```bash
./gradlew build                 # 전체 모듈 (compile + checkstyle + test)
./gradlew check                 # 컴파일 + Checkstyle + 테스트만
./gradlew :app:bootJar          # 실행 가능한 jar
```

> Checkstyle 위반은 빌드 실패 (`maxWarnings=0`). Java 변경 후엔 반드시 `./gradlew check` 통과 확인.

### 실행

```bash
# 로컬 (gradle 로 실행)
./gradlew :app:bootRun -Dspring.profiles.active=local

# 또는 빌드된 jar 로 실행 (.env 자동 로드)
./run-local.sh
```

### 테스트

```bash
./gradlew test                                # 전체
./gradlew :infra:persistence:test             # 단일 모듈
./gradlew :infra:persistence:test --tests <ClassName>
```

JPA 통합 테스트는 Testcontainers (`postgresql:16-alpine`) 위에서 Flyway 마이그레이션을 적용해 실행됩니다.

## 환경 설정

### 프로파일

| 프로파일 | 용도 | 비고 |
|----------|------|------|
| `local` | 개발자 로컬 | localhost 인프라 |
| `dev` | 개발 서버 | docker 네트워크 (`lol-postgres`, `lol-rabbitmq`, `lol-redis`) |
| `prod` | 운영 | 모든 호스트/자격증명을 환경변수로 주입 |

### 주요 환경변수

| 변수 | 설명 | 기본값 |
|------|------|--------|
| `RIOT_API_KEY` | Riot Developer API Key | (필수) |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | PostgreSQL 접속 | local: 1234 |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` / `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | RabbitMQ (prod) | local: guest/guest |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` | Redis (prod) | - |
| `LOL_VT_ENABLED` | Virtual Thread 사용 여부 | `true` |
| `LOL_VT_EXECUTORS_ENABLED` | Virtual Thread 기반 Executor 사용 | `LOL_VT_ENABLED` |
| `BACKFILL_*` | 매치 백필 잡 옵션 (`START_ID`, `END_ID`, `CHUNK_SIZE`, `PARALLELISM`, `FETCH_SIZE`, `SEASON`, `QUEUE_IDS`, `GCS_BUCKET`) | `app/src/main/resources/application.yml` 참조 |
| `MANAGEMENT_ENDPOINTS_EXPOSURE_INCLUDE` | Actuator 노출 엔드포인트 | `health,info,metrics,prometheus,threaddump` |

### `application-local.yml` 예시

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/postgres
    username: postgres
    password: 1234
  rabbitmq:
    host: localhost
    port: 5672
  data:
    redis:
      host: localhost
      port: 6379

riot:
  api-key: your-riot-api-key
```

## 코드 컨벤션

### 네이밍

- 엔티티: `*Entity` 접미사 (예: `MatchEntity`, `SummonerEntity`)
- 복합 키: `entity/id/` 패키지에 `*Id` 접미사
- 값 객체: `entity/value/` 패키지에 `*Value` 접미사
- 도메인 Port: `*RepositoryPort` / `*ApiPort` (`core:domain` 정의 → `infra:*` 에서 어댑터 구현)
- JPA Repository: `*JpaRepository`

### 헥사고날 / 의존 방향

- `core:domain` 은 Spring/JPA 의존 없는 순수 도메인. Port 인터페이스만 정의.
- `infra:*` 어댑터가 Port 를 구현 (DI 시점에 결합).

### 비동기 / 동시성

- `CompletableFuture` + 커스텀 `Executor` (Virtual Thread 기반).
- `@Async` 보다 명시적 `Executor` 주입을 선호.
- Hibernate 세션은 단일 스레드 — 동시성 분기 시 영속 상태 공유 금지.

### Lombok

- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본.
- `@Builder` 활용. Entity 에서 `@Setter` 지양.

## 개발 환경 요구사항

| 항목 | 버전 |
|------|------|
| JDK | 21 (Gradle Toolchain 자동 다운로드) |
| Gradle | Wrapper (`./gradlew`) |
| PostgreSQL | 14+ (테스트는 16) |
| Redis | 6+ |
| RabbitMQ | 3.13 |

## 추가 문서

- [`CLAUDE.md`](./CLAUDE.md) — Claude Code 작업용 프로젝트 가이드
- [`docs/testing-guide.md`](./docs/testing-guide.md) — 레이어별 테스트 전략
