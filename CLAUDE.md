# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 1. 프로젝트 개요

LOL Repository는 League of Legends 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서버의 부하를 분산하여 데이터베이스 작업과 RIOT API 통신을 Rate Limiting 및 메시지 큐 처리와 함께 담당합니다.

## 2. 기술 스택

- Java 21, Spring Boot 3.5.9, Gradle
- PostgreSQL (JPA/Hibernate)
- Redis + Redisson 3.46.0 (캐싱, 분산 락)
- RabbitMQ (비동기 메시지 처리)
- Flyway (DB 마이그레이션)

## 3. 프로젝트 구조

**수직 바운디드 컨텍스트 모듈** 구조 (lol-server 아키텍처 정렬). 의존은 항상 `infra → domain` 단방향이며, 모듈 단일 진실원천은 `settings.gradle`.

```
lol-repository (루트)
├── lol-db-schema/                  # Git 서브모듈 - Flyway 마이그레이션 SQL
├── buildSrc/                       # lolrepo.web/persistence-conventions 컨벤션 플러그인
└── module/
    ├── app/application             # Spring Boot 진입점 + 컴포지션 루트 + backfill 배치 (bootJar)
    ├── shared                      # 공유 enum (Platform/Tier/Queue/Division/EventType …)
    ├── common                      # 공유 커널: error(CoreException) + MDC 유틸 (도메인 아님)
    ├── support/logging             # @LogExecutionTime / MDC 로깅 AOP
    ├── domain/
    │   ├── league                  # 리그/랭킹/티어컷오프 (sink 컨텍스트)
    │   ├── champion                # 챔피언 로테이션
    │   ├── spectator               # 활성 게임 조회
    │   ├── match                   # 매치/타임라인 (→ league port 의존)
    │   └── summoner                # 소환사 (→ league port 의존)
    └── infra/                      # 어댑터(driven/driving) — 도메인 모듈에 의존
        ├── api                     # REST 컨트롤러 (driving)
        ├── persistence             # JPA 엔티티/리포지토리 구현 (driven)
        ├── redis                   # Redis 캐싱/분산 락 (driven)
        ├── rabbitmq                # 메시지 큐 리스너/서비스 (driving/driven)
        └── riot-client             # RIOT API RestClient + RateLimit (driven)
```

### 도메인 모듈 내부 구조 (module/domain/{ctx})

```
com.mmrtr.lol.domain.{ctx}/
├── domain/                 # 도메인 모델 (인프라 무지 — ArchUnit 강제)
├── application/
│   ├── port/               # Outbound Port (Repository/Api/Publish Port)
│   └── usecase/            # UseCase (Inbound Port)
└── readmodel/ (match)      # Riot/응답 DTO
```

> 컨텍스트 경계는 `module/domain/{ctx}/src/test` 의 `ArchitectureTest` 로 강제(`./gradlew archTest`).
> 인프라 어댑터(`module/infra/*`)는 도메인 모듈의 port 를 구현하며 도메인에 단방향 의존한다.

### 데이터 흐름

1. **Main Server** → **RabbitMQ** → **LOL Repository** → **RIOT API**
2. RestClient Interceptor 체인: `RetryInterceptor` (Retry-After 기반 backoff + ±25% jitter + 4xx/429/5xx/IOException 분류 + Micrometer 메트릭 3종 — `riot.api.responses{status,host}` / `riot.api.retry.attempts{outcome=success|exhausted}` / `riot.api.retry.backoff` timer) → 로컬 Redisson `RateLimitInterceptor` → 로깅 → `concurrencyInterceptor` (Semaphore 20)
3. CompletableFuture 패턴을 활용한 비동기 처리
4. 배치 데이터베이스 삽입 (배치 크기 1000, 1초 간격)

## 4. 명령어

```bash
# 서브모듈 초기화 (최초 클론 후)
git submodule update --init --recursive

# 서브모듈 최신화
git submodule update --remote lol-db-schema

# 전체 모듈 빌드
./gradlew build

# 애플리케이션 실행 (local 프로파일)
./gradlew :module:app:application:bootRun -Dspring.profiles.active=local

# 테스트 실행
./gradlew test

# 컨텍스트 경계(ArchUnit) 테스트만
./gradlew archTest

# Docker 없이 전 모듈 컴파일 검증 (리팩토링용)
./gradlew compileJava

# 코드 스타일 검증 (Checkstyle)
./gradlew check

# 특정 모듈 빌드
./gradlew :module:app:application:build
./gradlew :module:domain:summoner:build      # match / league / champion / spectator 동일
./gradlew :module:shared:build
./gradlew :module:common:build
./gradlew :module:support:logging:build
./gradlew :module:infra:persistence:build    # api / redis / rabbitmq / riot-client 동일
```

## 5. 코드 스타일

### 네이밍 컨벤션

- 엔티티: `*Entity` 접미사 (예: `MatchEntity`, `SummonerEntity`)
- 복합 키: `entity/id/` 패키지에 `*Id` 접미사
- 값 객체: `entity/value/` 패키지에 `*Value` 접미사

### Repository 패턴 (Hexagonal Architecture)

- `module:domain:{ctx}` 의 `application/port` 에 Port 인터페이스 정의 (예: `SummonerRepositoryPort`)
- `module:infra:persistence` 에 Adapter 구현체 (예: `SummonerRepositoryImpl`)
- JpaRepository 인터페이스는 `*JpaRepository` 네이밍 사용

### 비동기 처리

- CompletableFuture + Executor 패턴 사용
- `@Async` 어노테이션과 커스텀 Executor 조합

### Lombok 사용

- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본 사용
- `@Builder` 패턴 활용
- Entity에서 `@Setter` 사용 지양

## 6. 테스트 코드 작성

테스트 작성 시 [테스트 가이드](./docs/testing-guide.md)를 참조합니다.
- 레이어별 테스트 전략, 어노테이션, 모킹 규칙 등을 정의
- 기존 패턴: `module/infra/persistence`의 `SummonerRankingJpaRepositoryTest` 참조

## 7. 코드 변경 후 검증 (필수)

Java 코드를 추가/수정한 뒤에는 **반드시** 아래 중 하나를 실행해 검증한다.
컴파일 통과만으로는 부족 — 이 프로젝트는 Checkstyle 을 build 단계에서
강제하므로 위반이 있으면 CI 가 실패한다.

```bash
# 권장: 모듈 전체 검증 (compile + checkstyle + test)
./gradlew check

# 빠르게 단일 모듈만
./gradlew :module:app:application:check

# 배포 직전 풀 빌드
./gradlew build
```

`./gradlew :module:app:application:compileJava` 만 실행하면 **Checkstyle 이 돌지 않는다**.
컴파일 통과를 작업 완료의 기준으로 삼지 말 것.

가장 흔한 위반:
- `UnusedImports` — 미사용 import (특히 record 로 리팩토링 후 도우미 클래스 import 가 남는 경우)
- 미사용 변수 / 매개변수
- 라인 길이 초과

## 8. See Also

- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — 모듈 의존 그래프(mermaid) + 변경 영향표 + 잔여 결합
- 각 `module/domain/{ctx}/CLAUDE.md` — 컨텍스트별 책임/구조
- `./gradlew archTest` — 컨텍스트 경계(ArchUnit) 검증
