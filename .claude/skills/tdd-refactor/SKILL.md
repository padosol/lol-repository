---
name: tdd-refactor
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash(./gradlew test *)
  - Edit
  - Task
description: TDD Refactor 단계 - 테스트 통과 유지하며 코드 품질 개선
---

## Context

- 전체 테스트 상태: !`./gradlew test --info 2>&1 | tail -10`
- 현재 브랜치: !`git branch --show-current`

## 사용법

```
/tdd-refactor
```
또는
```
/tdd-refactor <대상파일경로>
```

## 리팩토링 체크리스트

### 코드 품질
- [ ] 중복 코드 제거 (DRY)
- [ ] 긴 메서드 분리 (Extract Method)
- [ ] 변수명/메서드명 명확화
- [ ] 매직 넘버 상수화
- [ ] 불필요한 주석 제거

### 설계 품질
- [ ] 단일 책임 원칙 (SRP) 준수
- [ ] 의존성 방향 확인 (infra → core)
- [ ] 포트 인터페이스 적절성
- [ ] 불필요한 public 메서드 private으로 변경

### 테스트 품질
- [ ] 테스트 가독성 개선
- [ ] 테스트 데이터 팩토리 메서드 추출
- [ ] 중복 설정 @BeforeEach로 이동
- [ ] 테스트명 명확화

## Your task

1. **현재 상태 확인**
   - 전체 테스트 실행 (`./gradlew test`)
   - Green 상태 확인 (모든 테스트 통과)

2. **코드 분석**
   - 최근 작성/수정된 코드 리뷰
   - 리팩토링 대상 식별
   - 우선순위 결정

3. **리팩토링 수행** (작은 단위로)
   - 하나의 리팩토링 적용
   - 테스트 실행
   - Green 유지 확인

4. **반복**
   - 추가 리팩토링 적용
   - 매번 테스트 실행으로 Green 확인
   - 깨지면 즉시 롤백

5. **결과 보고**
   - 수행된 리팩토링 목록
   - 최종 테스트 결과

## 리팩토링 패턴 예시

### Extract Method
Before:
```java
public void processData(List<Data> dataList) {
    // 유효성 검증
    for (Data data : dataList) {
        if (data.getValue() < 0) throw new Exception();
        if (data.getName() == null) throw new Exception();
    }
    // 처리 로직
    for (Data data : dataList) {
        // 복잡한 처리...
    }
}
```

After:
```java
public void processData(List<Data> dataList) {
    validateDataList(dataList);
    processValidatedData(dataList);
}

private void validateDataList(List<Data> dataList) {
    dataList.forEach(this::validateData);
}

private void validateData(Data data) {
    if (data.getValue() < 0) throw new Exception();
    if (data.getName() == null) throw new Exception();
}
```

### Replace Magic Number with Constant
Before:
```java
if (level >= 10) {
    freeChampions = getNewPlayerChampions();
}
```

After:
```java
private static final int NEW_PLAYER_MAX_LEVEL = 10;

if (level >= NEW_PLAYER_MAX_LEVEL) {
    freeChampions = getNewPlayerChampions();
}
```

### Extract Test Factory Method
Before:
```java
@Test
void test1() {
    Summoner summoner = new Summoner("puuid", 100L, 1234, "Name", "Tag", "kr", "name", now, now, null);
    // test...
}

@Test
void test2() {
    Summoner summoner = new Summoner("puuid2", 200L, 5678, "Name2", "Tag2", "kr", "name2", now, now, null);
    // test...
}
```

After:
```java
@Test
void test1() {
    Summoner summoner = createSummoner("puuid", "Name", "Tag");
    // test...
}

@Test
void test2() {
    Summoner summoner = createSummoner("puuid2", "Name2", "Tag2");
    // test...
}

private Summoner createSummoner(String puuid, String name, String tag) {
    return new Summoner(puuid, 100L, 1234, name, tag, "kr", name.toLowerCase(), LocalDateTime.now(), LocalDateTime.now(), null);
}
```

## 중요 규칙

- 리팩토링 중 새로운 기능 추가 금지
- 작은 단위로 변경 후 즉시 테스트
- 테스트가 깨지면 즉시 롤백
- 외부 동작 변경 금지 (내부 구조만 개선)
