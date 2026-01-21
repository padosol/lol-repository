# LOL Repository

## 프로젝트 소개

LOL Repository는 League of Legends 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서버의 부하를 분산하여 데이터베이스 작업과 RIOT API 통신을 Rate Limiting 및 메시지 큐 처리와 함께 담당합니다.

## 기술 스택

| 분류 | 기술 |
|------|------|
| Language | Java 17 |
| Framework | Spring Boot 3.3.1 |
| Build | Gradle |
| Database | PostgreSQL (JPA/Hibernate) |
| Cache | Redis + Redisson 3.46.0 |
| Message Queue | RabbitMQ |

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
├── lol-api/     # REST API 모듈, Spring Boot 애플리케이션 진입점
└── lol-core/    # 핵심 비즈니스 로직, 도메인, 서비스, 인프라
```

### lol-api
- Spring Boot 애플리케이션 진입점 (`LolRepositoryApplication.java`)
- REST API 엔드포인트
- 실행 가능한 bootJar 빌드

### lol-core
- DDD 패턴 기반 도메인 모듈 (summoner, match, league, champion, queue)
- RabbitMQ 리스너 및 메시지 처리
- RIOT API 클라이언트 추상화
- Redis 캐싱 및 분산 Rate Limiting

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
./gradlew :lol-api:build
./gradlew :lol-core:build
```

### 실행
```bash
# local 프로파일로 실행
./gradlew :lol-api:bootRun -Dspring.profiles.active=local
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
