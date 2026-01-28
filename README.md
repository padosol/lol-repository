# LOL Repository

## 프로젝트 소개

LOL Repository는 League of Legends 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서버의 부하를 분산하여 데이터베이스 작업과 RIOT API 통신을 Rate Limiting 및 메시지 큐 처리와 함께 담당합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.1 |
| Build | Gradle (Multi-Module) |
| Database | PostgreSQL (JPA/Hibernate) |
| Cache | Redis + Redisson 3.46.0 |
| Message Queue | RabbitMQ |
| Monitoring | Micrometer + Prometheus |

## 시스템 아키텍처

```
┌─────────────┐    ┌────────────┐    ┌────────────────┐    ┌──────────┐
│ Main Server │ -> │  RabbitMQ  │ -> │ LOL Repository │ -> │ RIOT API │
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
├── app/                    # Spring Boot 애플리케이션 진입점
├── core/
│   ├── domain/             # DDD 기반 도메인 모델 및 서비스
│   └── enum/               # 공통 열거형 정의
├── infra/
│   ├── api/                # REST API 엔드포인트
│   ├── persistence/        # JPA 엔티티 및 Repository
│   ├── redis/              # Redis 캐싱 및 분산 락
│   ├── rabbitmq/           # 메시지 큐 리스너 및 발행
│   └── riot-client/        # RIOT API 클라이언트 (Rate Limiting 포함)
└── support/                # 공통 유틸리티 및 예외 처리
```

### 모듈 설명

| 모듈 | 설명 |
|------|------|
| `app` | Spring Boot 메인 애플리케이션. 모든 모듈을 조합하여 실행 가능한 jar 생성 |
| `core:domain` | 비즈니스 도메인 모델 (Summoner, Match, League, Champion 등) |
| `core:enum` | 공통으로 사용되는 열거형 (Region, Platform, Queue Type 등) |
| `infra:api` | REST 컨트롤러 및 요청/응답 DTO |
| `infra:persistence` | JPA 엔티티, Repository, 데이터베이스 설정 |
| `infra:redis` | Redis 기반 캐싱 및 분산 락 구현 |
| `infra:rabbitmq` | RabbitMQ 메시지 리스너 및 Producer |
| `infra:riot-client` | RIOT API 클라이언트, Rate Limiting AOP |
| `support` | 공통 예외, 유틸리티, 에러 핸들링 |

### 모듈 의존성

```
                          ┌─────────────────┐
                          │       app       │
                          └────────┬────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
┌───────────────┐         ┌────────────────┐         ┌────────────────┐
│  infra:api    │         │ infra:rabbitmq │         │infra:riot-client│
└───────────────┘         └────────────────┘         └────────────────┘
        │                          │                          │
        └──────────────────────────┼──────────────────────────┘
                                   │
        ┌──────────────────────────┼──────────────────────────┐
        │                          │                          │
        ▼                          ▼                          ▼
┌────────────────┐        ┌────────────────┐         ┌────────────────┐
│infra:persistence│        │  infra:redis   │         │  core:domain   │
└────────────────┘        └────────────────┘         └────────────────┘
        │                          │                          │
        └──────────────────────────┼──────────────────────────┘
                                   │
                          ┌────────┴────────┐
                          │                 │
                          ▼                 ▼
                   ┌────────────┐    ┌───────────┐
                   │ core:enum  │    │  support  │
                   └────────────┘    └───────────┘
```

## 데이터 흐름

```
1. SummonerRenewalListener
   └─> RIOT API (matchList 조회)
       └─> 매치 ID 목록 획득

2. Rate Limited Queue
   └─> 매치 ID를 RabbitMQ로 발행 (초당 20개 제한)

3. MatchFindListener
   └─> RIOT API (match/timeline 조회)
       └─> 매치 상세 정보 획득

4. 배치 저장
   └─> PostgreSQL (배치 크기 1000, 1초 간격)
```

## Rate Limiting

### 구현 방식
- **Redisson 기반 분산 락**: 클러스터 환경에서 전역적으로 Rate Limit 적용
- **Token Bucket Algorithm**: 초당 20개 요청 제한
- **AOP 기반 적용**: `@RateLimited` 어노테이션으로 선언적 적용

### RIOT API Rate Limit
| 범위 | 제한 |
|------|------|
| REGION | 460 requests / 10 seconds |
| PLATFORM | 350 requests / 10 seconds |

## 빌드 및 실행

### 빌드
```bash
# 전체 모듈 빌드
./gradlew build

# 특정 모듈 빌드
./gradlew :app:build
./gradlew :core:domain:build
./gradlew :infra:persistence:build
```

### 실행
```bash
# local 프로파일로 실행
./gradlew :app:bootRun -Dspring.profiles.active=local
```

### 테스트
```bash
./gradlew test
```

## 환경 설정

### 필수 환경 변수

| 변수 | 설명 |
|------|------|
| `RIOT_API_KEY` | RIOT Developer API Key |
| `DB_HOST` | PostgreSQL 호스트 |
| `DB_PORT` | PostgreSQL 포트 |
| `DB_NAME` | 데이터베이스 이름 |
| `DB_USERNAME` | 데이터베이스 사용자 |
| `DB_PASSWORD` | 데이터베이스 비밀번호 |
| `REDIS_HOST` | Redis 호스트 |
| `REDIS_PORT` | Redis 포트 |
| `RABBITMQ_HOST` | RabbitMQ 호스트 |
| `RABBITMQ_PORT` | RabbitMQ 포트 |

### application-local.yml 설정 예시

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/lol
    username: postgres
    password: password

  rabbitmq:
    host: localhost
    port: 5672

  data:
    redis:
      host: localhost
      port: 6379

riot:
  api:
    key: your-riot-api-key
```

## 코드 컨벤션

### 네이밍
- 엔티티: `*Entity` 접미사 (예: `MatchEntity`, `SummonerEntity`)
- 복합 키: `*Id` 접미사
- Repository: JPA 인터페이스 + 커스텀 Repository 이중 구조

### 비동기 처리
- CompletableFuture + Executor 패턴 사용
- `@Async` 어노테이션과 커스텀 Executor 조합

### Lombok 사용
- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본 사용
- `@Builder` 패턴 활용
- Entity에서 `@Setter` 사용 지양

## 개발 환경 요구사항

| 항목 | 버전 |
|------|------|
| JDK | 17 이상 |
| Gradle | 8.x (Wrapper 포함) |
| PostgreSQL | 14 이상 |
| Redis | 6 이상 |
| RabbitMQ | 3.x |
