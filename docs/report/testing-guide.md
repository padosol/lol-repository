# 테스트 코드 작성 가이드

## 공통 규칙

- **테스트 프레임워크**: JUnit 5 + AssertJ + Mockito (spring-boot-starter-test)
- **구조**: Given-When-Then 패턴 (주석으로 `// given`, `// when`, `// then` 명시)
- **네이밍**: `@DisplayName`에 한글로 기능 설명, 메서드명은 `methodName_condition_expectedBehavior`
- **검증**: AssertJ 사용 (`assertThat(...).isEqualTo(...)`)
- **모킹**: Port 인터페이스를 `@Mock`으로 모킹, 구체 클래스는 모킹하지 않음

## 레이어별 테스트 전략

### core/domain - 도메인 모델 (단위 테스트)

- Spring 컨텍스트 없이 순수 Java 테스트
- 팩토리 메서드(`Summoner.create()` 등), 비즈니스 로직, Value Object 동등성 검증

### core/domain - Application Service / UseCase (단위 테스트)

- `@ExtendWith(MockitoExtension.class)` 사용
- Port 인터페이스를 `@Mock`, 테스트 대상을 `@InjectMocks`
- CompletableFuture 반환 Port: `when(...).thenReturn(CompletableFuture.completedFuture(...))`
- CoreException 발생 검증: `assertThatThrownBy(...).isInstanceOf(CoreException.class)`

### infra/persistence - JPA Repository (통합 테스트)

- 기존 패턴 참조: `SummonerRankingJpaRepositoryTest`
- `@DataJpaTest` + `@ActiveProfiles("test")` + `@Import(JpaConfig.class)`
- `@AutoConfigureTestDatabase(replace = Replace.NONE)` (Testcontainers PostgreSQL)
- `application-test.yml`의 `jdbc:tc:postgresql:16-alpine:///test` 활용
- EntityManager로 데이터 준비 (`flush/clear`), `@BeforeEach`에서 초기화
- `TestPersistenceConfig`를 테스트 부트스트랩으로 사용

### infra/riot-client - API 어댑터 (단위 테스트)

- `@ExtendWith(MockitoExtension.class)`로 `RiotApiService`를 `@Mock`
- CompletableFuture 체이닝 및 `exceptionally` 에러 변환 검증
- RiotClientException → CoreException 변환 로직 테스트

### infra/rabbitmq - 메시지 리스너/서비스 (단위 테스트)

- `@ExtendWith(MockitoExtension.class)` 사용
- RedisLockHandler, 도메인 서비스를 `@Mock`
- 락 획득 성공/실패, 예외 시 락 해제 검증 (`verify(...)`)

### infra/redis - 분산락/캐시 (단위 테스트)

- `@ExtendWith(MockitoExtension.class)` 사용
- RedissonClient, RLock을 `@Mock`
- `executeWithLock` 성공/실패 시나리오 검증

### infra/api - REST 컨트롤러 (슬라이스 테스트)

- `@WebMvcTest(대상Controller.class)` + `MockMvc`
- 도메인 서비스를 `@MockBean`으로 모킹
- 정상 응답 및 CoreException 에러 응답 검증
- `MockMvcResultMatchers.jsonPath()` 활용

## 테스트 파일 위치 규칙

- 각 모듈의 `src/test/java/` 하위에 메인 소스와 동일한 패키지 구조
- 테스트 클래스명: `{대상클래스}Test`
- persistence 통합 테스트 설정: `src/test/resources/application-test.yml`
- persistence 테스트 부트스트랩: `TestPersistenceConfig` (@SpringBootApplication)
