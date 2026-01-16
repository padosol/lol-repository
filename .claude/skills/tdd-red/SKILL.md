---
name: tdd-red
allowed-tools:
  - Read
  - Glob
  - Grep
  - Bash(./gradlew test *)
  - Write
  - Task
description: TDD Red 단계 - 실패하는 테스트 먼저 작성
---

## Context

- 프로젝트 테스트 패턴: !`cat CLAUDE.md | grep -A 30 "테스트 작성 규칙"`
- 현재 브랜치: !`git branch --show-current`

## 사용법

```
/tdd-red <대상클래스>.<메서드명> <테스트유형>
```

### 테스트 유형
- `domain` - 도메인 서비스 테스트 (기본값)
- `restdocs` - RestDocs 컨트롤러 테스트
- `adapter` - 어댑터 통합 테스트

### 예시
```
/tdd-red SummonerService.deleteSummoner domain
/tdd-red SummonerController.getSummoner restdocs
/tdd-red SummonerPersistenceAdapter.save adapter
```

## 테스트 유형별 템플릿

### 1. 도메인 서비스 테스트 (domain)

**위치**: `module/core/lol-server-domain/src/test/java/com/example/lolserver/domain/{도메인}/application/{Service}Test.java`

```java
@ExtendWith(MockitoExtension.class)
class {Service}Test {

    @Mock
    private {Port} {port};

    @InjectMocks
    private {Service} {service};

    @DisplayName("한글로 테스트 설명")
    @Test
    void methodName_조건_결과() {
        // given
        given({port}.method(any())).willReturn(expected);

        // when
        var result = {service}.method(input);

        // then
        assertThat(result).isEqualTo(expected);
        then({port}).should().method(any());
    }
}
```

**필수 import**:
```java
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
```

### 2. RestDocs 컨트롤러 테스트 (restdocs)

**위치**: `module/infra/api/src/test/java/com/example/lolserver/docs/controller/{Controller}Test.java`

```java
@ExtendWith(MockitoExtension.class)
class {Controller}Test extends RestDocsSupport {

    @Mock
    private {Service} {service};

    @InjectMocks
    private {Controller} {controller};

    @Override
    protected Object initController() {
        return {controller};
    }

    private final String BASE_URL = "/api/v1/{도메인}";

    @DisplayName("API 설명")
    @Test
    void apiMethod() throws Exception {
        // given
        given({service}.method(any())).willReturn(response);

        // when
        ResultActions result = mockMvc.perform(
            get(BASE_URL + "/{param}", value));

        // then
        result.andExpect(status().isOk())
            .andDo(document("document-name",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()),
                pathParameters(
                    parameterWithName("param").description("파라미터 설명")
                ),
                responseFields(
                    fieldWithPath("result").type(JsonFieldType.STRING).description("API 결과"),
                    fieldWithPath("data").type(JsonFieldType.OBJECT).description("응답 데이터"),
                    fieldWithPath("errorMessage").type(JsonFieldType.NULL).description("에러 메시지")
                )
            ));
    }
}
```

### 3. 어댑터 테스트 (adapter)

**위치**: `module/infra/persistence/postgresql/src/test/java/com/example/lolserver/repository/{도메인}/adapter/{Adapter}Test.java`

```java
class {Adapter}Test extends RepositoryTestBase {

    @Autowired
    private {JpaRepository} repository;

    @Autowired
    private {Mapper} mapper;

    @Autowired
    private EntityManager entityManager;

    private {Adapter} adapter;

    @BeforeEach
    void setUp() {
        adapter = new {Adapter}(repository, mapper);
    }

    @DisplayName("테스트 설명")
    @Test
    void methodName_condition_result() {
        // given
        {Entity} entity = createEntity();
        repository.save(entity);
        entityManager.flush();
        entityManager.clear();

        // when
        var result = adapter.method(param);

        // then
        assertThat(result).satisfies(r -> {
            assertThat(r.getField()).isEqualTo(expected);
        });
    }
}
```

## Your task

1. **대상 분석**
   - 대상 클래스 파일 찾기 및 읽기
   - 메서드 시그니처 파악
   - 의존성(포트) 인터페이스 식별

2. **포트 분석** (domain/adapter 유형인 경우)
   - 관련 포트 인터페이스 분석
   - Mock 설정에 필요한 메서드 파악

3. **테스트 코드 생성**
   - 적절한 템플릿 선택
   - BDD 스타일로 테스트 작성 (given-when-then)
   - 메서드명: 영어 (`methodName_조건_결과`)
   - @DisplayName: 한글

4. **테스트 실행**
   - `./gradlew test --tests "{TestClass}.{testMethod}"` 실행
   - 실패 확인 (Red 상태)

5. **결과 보고**
   - 생성된 테스트 코드 출력
   - 실패 메시지 출력 (컴파일 에러 또는 assertion 실패)

## 중요 규칙

- 테스트는 반드시 실패해야 함 (구현이 없거나 불완전한 상태)
- given-when-then 주석 필수
- Happy Path + Exception 케이스 모두 작성
- 기존 테스트 패턴 준수
