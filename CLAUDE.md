# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 1. 프로젝트 개요

LOL Repository는 League of Legends 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서버의 부하를 분산하여 데이터베이스 작업과 RIOT API 통신을 Rate Limiting 및 메시지 큐 처리와 함께 담당합니다.

## 2. 기술 스택

- Java 17, Spring Boot 3.3.1, Gradle
- PostgreSQL (JPA/Hibernate)
- Redis + Redisson 3.46.0 (캐싱, 분산 Rate Limiting)
- RabbitMQ (비동기 메시지 처리)

## 3. 프로젝트 구조

### 모듈 구조

- **lol-api**: REST API 모듈, Spring Boot 애플리케이션 진입점 (`LolRepositoryApplication.java`). 실행 가능한 bootJar 빌드.
- **lol-core**: 핵심 비즈니스 로직 모듈, 도메인, 서비스, 인프라 포함. 라이브러리 jar 빌드.

### 주요 패키지 구조 (lol-core)

```
com.mmrtr.lol/
├── config/          # Spring 설정 (Redis, RabbitMQ, Async, Rate Limiting)
├── domain/          # DDD 패턴을 따르는 도메인 모듈
│   ├── summoner/    # 소환사 데이터 (entity/, repository/, service/, domain/)
│   ├── match/       # 매치 데이터 및 타임라인 이벤트
│   ├── league/      # 리그/랭킹 데이터
│   ├── champion/    # 챔피언 로테이션 데이터
│   └── queue/       # 큐 메타데이터
├── rabbitmq/        # RabbitMQ 리스너 및 메시지 처리
├── redis/           # Redis 작업 및 분산 캐싱
├── riot/            # RIOT API 클라이언트 추상화
│   ├── core/        # 요청 빌더 및 실행
│   ├── dto/         # API 응답 DTO
│   ├── service/     # RiotApiService
│   └── aspect/      # Rate Limiting AOP
└── support/         # 에러 처리 유틸리티
```

### 데이터 흐름

1. **Main Server** → **RabbitMQ** → **LOL Repository** → **RIOT API**
2. Redisson을 통한 Rate Limiting (클러스터 전역 초당 20개 요청)
3. CompletableFuture 패턴을 활용한 비동기 처리
4. 배치 데이터베이스 삽입 (배치 크기 1000, 1초 간격)

## 4. 명령어

```bash
# 전체 모듈 빌드
./gradlew build

# 애플리케이션 실행 (local 프로파일)
./gradlew :lol-api:bootRun -Dspring.profiles.active=local

# 테스트 실행 (현재 build.gradle에서 비활성화됨)
./gradlew test

# 특정 모듈 빌드
./gradlew :lol-api:build
./gradlew :lol-core:build
```

## 5. 코드 스타일

### 네이밍 컨벤션

- 엔티티: `*Entity` 접미사 (예: `MatchEntity`, `SummonerEntity`)
- 복합 키: `entity/id/` 패키지에 `*Id` 접미사
- 값 객체: `entity/value/` 패키지에 `*Value` 접미사

### Repository 패턴

- JpaRepository 인터페이스 + 커스텀 Repository 이중 구조
- 예: `MatchJpaRepository` (JPA) + `MatchRepository` (비즈니스 로직)

### 비동기 처리

- CompletableFuture + Executor 패턴 사용
- `@Async` 어노테이션과 커스텀 Executor 조합

### Lombok 사용

- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본 사용
- `@Builder` 패턴 활용
- Entity에서 `@Setter` 사용 지양
