---
name: tdd-green
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash(./gradlew test *)
  - Write
  - Edit
  - Task
description: TDD Green 단계 - 최소한의 구현으로 테스트 통과
---

## Context

- 현재 실패 테스트: !`./gradlew test 2>&1 | grep -A 5 "FAILED" | head -20`
- 프로젝트 아키텍처: 헥사고날 (Ports & Adapters)

## 사용법

```
/tdd-green
```
또는
```
/tdd-green <TestClass>.<testMethod>
```

## 구현 원칙

### 1. 최소 구현 (YAGNI)
- 테스트를 통과시키는 최소한의 코드만 작성
- 불필요한 기능 추가 금지
- 하드코딩도 괜찮음 (나중에 리팩토링)

### 2. 헥사고날 아키텍처 준수

**의존성 방향**: `infra → core` (절대로 역방향 금지)

| 레이어 | 허용 의존성 |
|--------|------------|
| core (domain) | 표준 Java, Lombok, 순수 인터페이스 |
| infra (api) | core, Spring Web |
| infra (persistence) | core, Spring Data JPA |
| infra (client) | core, RestClient |

### 3. 구현 위치 가이드

| 테스트 유형 | 구현 위치 |
|------------|----------|
| 도메인 서비스 | `module/core/lol-server-domain/src/main/.../application/` |
| 포트 인터페이스 | `module/core/lol-server-domain/src/main/.../application/port/` |
| 컨트롤러 | `module/infra/api/src/main/.../controller/` |
| 영속성 어댑터 | `module/infra/persistence/postgresql/src/main/.../adapter/` |
| 클라이언트 어댑터 | `module/infra/client/lol-repository/src/main/.../adapter/` |

## Your task

1. **실패 테스트 분석**
   - 실패한 테스트 코드 읽기
   - 필요한 메서드/클래스 파악
   - Mock 설정에서 예상 동작 파악

2. **구현 계획**
   - 필요한 인터페이스(포트) 변경 파악
   - 구현 클래스 위치 결정
   - 최소 구현 범위 결정

3. **코드 작성**
   - 포트 인터페이스에 메서드 추가 (필요 시)
   - 서비스/어댑터에 구현 추가
   - 아키텍처 규칙 준수

4. **테스트 실행**
   - `./gradlew test --tests "{TestClass}"` 실행
   - Green 상태 확인

5. **반복** (필요 시)
   - 여전히 실패하면 수정 후 재실행
   - 모든 관련 테스트 통과까지 반복

## 체크리스트

실행 전:
- [ ] 실패 테스트가 명확히 파악되었는가?
- [ ] 최소 구현 범위가 결정되었는가?

실행 후:
- [ ] 테스트가 통과하는가?
- [ ] 과도한 구현이 아닌가?
- [ ] 아키텍처 규칙을 위반하지 않았는가?
- [ ] 기존 테스트가 깨지지 않았는가?

## 예시: 도메인 서비스 구현

테스트 (Red):
```java
@Test
void deleteSummoner_validPuuid_deleted() {
    given(summonerPersistencePort.existsById("puuid")).willReturn(true);

    summonerService.deleteSummoner("puuid");

    then(summonerPersistencePort).should().deleteById("puuid");
}
```

포트 추가:
```java
// SummonerPersistencePort.java
boolean existsById(String puuid);
void deleteById(String puuid);
```

서비스 구현:
```java
// SummonerService.java
public void deleteSummoner(String puuid) {
    if (!summonerPersistencePort.existsById(puuid)) {
        throw new CoreException(ErrorType.NOT_FOUND_PUUID);
    }
    summonerPersistencePort.deleteById(puuid);
}
```

## 중요

- Green 상태가 되면 즉시 멈추기
- 추가 기능이나 리팩토링은 /tdd-refactor에서
- 테스트가 요구하는 것만 구현
