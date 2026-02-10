# CLAUDE.md

이 파일은 Claude Code (claude.ai/code)가 이 저장소에서 작업할 때 참고하는 가이드입니다.

## 1. 프로젝트 개요

LOL Repository는 League of Legends 데이터 처리를 위한 컨슈머 서비스입니다. 메인 서버의 부하를 분산하여 데이터베이스 작업과 RIOT API 통신을 Rate Limiting 및 메시지 큐 처리와 함께 담당합니다.

## 2. 기술 스택

- Java 17, Spring Boot 3.3.1, Gradle
- PostgreSQL (JPA/Hibernate)
- Redis + Redisson 3.46.0 (캐싱, 분산 락)
- RabbitMQ (비동기 메시지 처리)

## 3. 프로젝트 구조

### 모듈 구조

```
lol-repository (루트)
├── app                    # Spring Boot 애플리케이션 진입점 (LolRepositoryApplication.java)
├── core/
│   ├── domain             # DDD 도메인 비즈니스 로직 (Port 인터페이스, UseCase, Service)
│   └── enum               # 공유 enum 타입들 (Region, Platform 등)
├── infra/
│   ├── api                # REST API 컨트롤러
│   ├── persistence        # JPA 엔티티 및 리포지토리 구현 (Adapter)
│   ├── redis              # Redis 캐싱 및 분산 락
│   ├── rabbitmq           # 메시지 큐 리스너/서비스
│   └── riot-client        # RIOT API 클라이언트 추상화
└── support                # 에러 처리 유틸리티
```

### 도메인 구조 (core/domain)

```
com.mmrtr.lol.domain/
├── summoner/    # 소환사 데이터
├── match/       # 매치 데이터 및 타임라인 이벤트
├── league/      # 리그/랭킹 데이터
├── champion/    # 챔피언 로테이션 데이터
└── spectator/   # 활성 게임 조회
```

### 데이터 흐름

1. **Main Server** → **RabbitMQ** → **LOL Repository** → **RIOT API**
2. RestClient Interceptor를 통한 Rate Limiting (`RetryInterceptor`)
3. CompletableFuture 패턴을 활용한 비동기 처리
4. 배치 데이터베이스 삽입 (배치 크기 1000, 1초 간격)

## 4. 명령어

```bash
# 전체 모듈 빌드
./gradlew build

# 애플리케이션 실행 (local 프로파일)
./gradlew :app:bootRun -Dspring.profiles.active=local

# 테스트 실행 (현재 build.gradle에서 비활성화됨)
./gradlew test

# 특정 모듈 빌드
./gradlew :app:build
./gradlew :core:domain:build
./gradlew :core:enum:build
./gradlew :infra:api:build
./gradlew :infra:persistence:build
./gradlew :infra:redis:build
./gradlew :infra:rabbitmq:build
./gradlew :infra:riot-client:build
./gradlew :support:build
```

## 5. 코드 스타일

### 네이밍 컨벤션

- 엔티티: `*Entity` 접미사 (예: `MatchEntity`, `SummonerEntity`)
- 복합 키: `entity/id/` 패키지에 `*Id` 접미사
- 값 객체: `entity/value/` 패키지에 `*Value` 접미사

### Repository 패턴 (Hexagonal Architecture)

- `core:domain`에 Port 인터페이스 정의 (예: `SummonerRepositoryPort`)
- `infra:persistence`에 Adapter 구현체 (예: `SummonerRepositoryImpl`)
- JpaRepository 인터페이스는 `*JpaRepository` 네이밍 사용

### 비동기 처리

- CompletableFuture + Executor 패턴 사용
- `@Async` 어노테이션과 커스텀 Executor 조합

### Lombok 사용

- `@Getter`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)` 기본 사용
- `@Builder` 패턴 활용
- Entity에서 `@Setter` 사용 지양

## 6. 주요 클래스 레퍼런스

자세한 내용은 [docs/class-reference.md](docs/class-reference.md)를 참조하세요.
