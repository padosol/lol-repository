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
| Framework | Spring Boot 3.5.9 |
| Build | Gradle (Multi-Module) |
| Database | PostgreSQL + JPA/Hibernate (`reWriteBatchedInserts`, `batch_size=1000`) |
| Migration | Flyway (`lol-db-schema` Git 서브모듈) |
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
├── lol-db-schema/          # Git 서브모듈 — Flyway 마이그레이션 SQL
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
   └─> RabbitMQ (mmrtr.summoner / renewal.match.find.queue / mmrtr.matchId)

2. SummonerRenewalListener
   └─> Riot API: 소환사·매치 ID 목록 조회
       └─> MatchIdPublisher → mmrtr.matchId 큐로 매치 ID 재발행

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
| `mmrtr.matchId` | `mmrtr.matchId.exchange` | `mmrtr.routingkey.matchId` | 매치 ID 분배 |
| `renewal.match.find.queue` | `renewal.topic.exchange` | `renewal.match.find` | 매치 상세 수집 |

## Rate Limiting & 배치 삽입

Riot API Rate Limit 은 **3-단계 디펜스** 로 보호합니다:

1. **소비 속도 제한 (글로벌)** — `ListenerRateLimiterConfig` 가 Redisson `RRateLimiter`
   `global:api:call:limiter` 를 **20 req/s** 로 설정. 모든 Riot 호출 직전에 토큰 획득.
2. **호스트별 Rate Limiter** — `RateLimitInterceptor` 가 요청 호스트를
   `HostRateLimitResolver` 로 분류해 각각 분산 RateLimiter 적용.

   | 종류 | 호스트 예시 | 제한 |
   |------|-------------|------|
   | `REGION_RATE_LIMITER` | `asia.api.riotgames.com`, `europe.api.riotgames.com` 등 | 460 req / 10 s |
   | `PLATFORM_RATE_LIMITER` | `kr.api.riotgames.com` 등 플랫폼 | 450 req / 10 s |

3. **동시성 제한** — `RiotApiConfig` 의 `Semaphore(20)` 인터셉터로 동시 요청 수를
   상한 20 으로 고정.

PostgreSQL 적재는 두 단계로 흡수:
- **인-메모리 큐 + 1 초 flush** — `MatchBatchProcessor` 가 매치를 모아 `@Scheduled(fixedRate=1000)` 로 일괄 flush.
- **JDBC 배치** — Hibernate `batch_size=1000`, `order_inserts/updates`, JDBC URL 에 `reWriteBatchedInserts=true`.

## 빌드 & 실행

### 사전 준비

```bash
# 최초 클론 후 서브모듈 초기화 (lol-db-schema)
git submodule update --init --recursive

# 서브모듈 최신화
git submodule update --remote lol-db-schema
```

### 인프라 (Docker Compose)

```bash
docker compose -f docker/docker-compose.yml up -d postgres rabbitmq redis
```

기본 포트: PostgreSQL `5432`, RabbitMQ `5672` (관리 UI `15672`), Redis `6379`.

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
