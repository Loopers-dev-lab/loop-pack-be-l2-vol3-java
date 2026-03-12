# 테스트 컨벤션

## 1. 프레임워크 및 도구

- **JUnit 5** + **AssertJ** (`assertThat`, `assertThatThrownBy`) + **Mockito** (`mock`, `stub`, `verify`)
- **@SpringBootTest**: 통합/E2E 테스트
- **TestRestTemplate**: E2E HTTP 요청
- **DatabaseCleanUp**: 테스트 간 DB 격리

---

## 2. 테스트 피라미드 --- 계층별 전략

### 단위 테스트 (Unit Test)

| 항목 | 내용 |
|------|------|
| 대상 | Entity, Domain Service |
| 환경 | **Spring 없이 순수 JVM** |
| 테스트 더블 | **Fake 우선**, 필요 시 Mockito |
| 비중 | 가장 많이 작성 |

```java
class UserModelTest {
    @Test
    void create_whenAllDataProvided() {
        UserModel user = UserModel.create("testuser", "encPw", "홍길동", LocalDate.of(2000, 1, 1), "test@email.com");
        assertThat(user.getName()).isEqualTo("홍길동");
    }
}
```

### 통합 테스트 (Integration Test)

| 항목 | 내용 |
|------|------|
| 대상 | Service, Facade (여러 컴포넌트 연결 상태) |
| 환경 | `@SpringBootTest`, 실제 Bean, Test DB |
| 테스트 더블 | **실제 Bean 사용** (DB 포함) |

```java
@SpringBootTest
class UserServiceIntegrationTest {
    @Autowired UserService userService;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }
}
```

### E2E 테스트 (End-to-End Test)

| 항목 | 내용 |
|------|------|
| 대상 | Controller -> Service -> DB 전체 |
| 환경 | `@SpringBootTest(webEnvironment = RANDOM_PORT)` |
| 도구 | `TestRestTemplate` |
| 비중 | 주요 시나리오만 선별 |

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UserV1ApiE2ETest {
    @Autowired TestRestTemplate testRestTemplate;
    @Autowired DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() { databaseCleanUp.truncateAllTables(); }
}
```

---

## 3. 테스트 클래스 구조

### @Nested로 행위별 그룹핑

`@Nested`로 행위 단위로 그룹핑한다. 부모 `@DisplayName`에 행위를, 자식에 조건+결과를 작성한다.

```java
class UserModelTest {

    @DisplayName("유저 모델을 생성할 때, ")
    @Nested
    class Create {

        @DisplayName("모든 필드가 주어지면, 정상적으로 생성된다.")
        @Test
        void create_whenAllDataProvided() {
            // act
            UserModel user = UserModel.create(
                    "testuser123", "encryptedPw", "홍길동",
                    LocalDate.of(2000, 1, 1), "test@email.com");

            // assert
            assertAll(
                    () -> assertThat(user.getLoginId()).isEqualTo("testuser123"),
                    () -> assertThat(user.getName()).isEqualTo("홍길동")
            );
        }

        @DisplayName("로그인 ID가 누락되면 예외가 발생한다.")
        @Test
        void create_whenLoginIdIsNull() {
            assertThatThrownBy(() -> UserModel.create(
                    null, "encryptedPw", "홍길동",
                    LocalDate.of(2000, 1, 1), "test@email.com"))
                .isInstanceOf(CoreException.class);
        }
    }
}
```

### 테스트 메서드 내부 구조: arrange / act / assert

주석으로 세 섹션을 구분한다. arrange가 없으면 생략 가능. 예외 검증처럼 한 줄로 끝나면 주석 없이 작성. 여러 필드를 한번에 검증할 때 `assertAll`을 사용한다 (위 @Nested 예제 참조).

### @ParameterizedTest로 경계값 테스트

```java
@DisplayName("2자 이상 10자 이하의 이름이 주어지면, 정상적으로 생성된다.")
@ParameterizedTest
@ValueSource(strings = {"홍길", "홍길동", "가나다라마바사아자차"})
void create_whenValidNameProvided(String validName) {
    UserModel user = UserModel.create(
            "testuser", "encPw", validName,
            LocalDate.of(2000, 1, 1), "test@email.com");
    assertThat(user.getName()).isEqualTo(validName);
}
```

---

## 4. 네이밍 규칙

### 테스트 클래스명

| 테스트 유형 | 클래스명 패턴 | 예시 |
|-----------|-----------|------|
| 단위 (Entity) | `{클래스명}Test` | `UserModelTest`, `OrderModelTest` |
| 단위 (Domain Service) | `{클래스명}Test` | `OrderServiceTest` |
| 통합 | `{클래스명}IntegrationTest` | `UserServiceIntegrationTest` |
| E2E | `{API명}E2ETest` | `UserV1ApiE2ETest` |

### 테스트 메서드명

**영문 camelCase** + `@DisplayName` 한글 조합. 패턴: `{action}_{condition}`

```java
@DisplayName("로그인 ID가 누락되면 예외가 발생한다.")
@Test
void createUserModel_whenLoginIdIsNull() { ... }
```

조건이 없는 성공 케이스는 `_success` 또는 `_whenAllDataProvided`를 사용한다.

### @DisplayName 규칙

| 위치 | 형식 | 예시 |
|------|------|------|
| `@Nested` 클래스 | `"{행위}할 때, "` | `"유저 모델을 생성할 때, "` |
| `@Test` 메서드 | `"{조건}이면, {결과}한다."` | `"로그인 ID가 누락되면 예외가 발생한다."` |

부모 + 자식을 이어 읽으면 자연스러운 한국어 문장이 된다:
> "유저 모델을 생성할 때, 로그인 ID가 누락되면 예외가 발생한다."

---

## 5. 테스트 더블 전략

### 계층별 테스트 더블 선택

| 테스트 대상 | 더블 전략 | 이유 |
|-----------|---------|------|
| **Entity** | 더블 불필요 (순수 로직) | 외부 의존 없음 |
| **Domain Service** | **Fake 우선** | 실제 동작과 유사, 상태 검증 가능 |
| **Application Facade** | **Mockito mock()** | 여러 Service 조합, Fake 비용 큼 |
| **통합 / E2E** | **실제 Bean** | 연동 검증이 목적 |

### Fake -- Domain 단위 테스트의 기본

```java
public class FakePasswordEncoder implements PasswordEncoder {

    @Override
    public String encode(String rawPassword) {
        return "ENCODED_" + rawPassword;
    }

    @Override
    public boolean matches(String rawPassword, String encodedPassword) {
        return encodedPassword.equals("ENCODED_" + rawPassword);
    }
}
```

### Mockito -- Application 계층 테스트

```java
@ExtendWith(MockitoExtension.class)
class OrderFacadeTest {

    @Mock OrderService orderService;
    @Mock ProductService productService;
    @InjectMocks OrderFacade orderFacade;

    @Test
    void createOrder_success() {
        // arrange (stub)
        when(productService.getProduct(1L)).thenReturn(productInfo);
        when(orderService.create(any())).thenReturn(order);

        // act
        OrderDetailResult result = orderFacade.createOrder(criteria);

        // assert
        assertThat(result).isNotNull();
        verify(orderService).create(any());
    }
}
```

### 테스트 더블 선택 규칙

- 외부 의존이 없으면 더블 불필요 (Entity)
- 의존이 인터페이스로 분리되어 있고, 상태 연동이 중요하면 **Fake**
- 의존이 인터페이스로 분리되어 있고, 단순 위임이면 **Mockito mock()**
- 인터페이스 분리가 없으면 **Mockito mock()**

---

## 6. 테스트 패키지 배치

테스트 클래스는 **프로덕션 코드와 동일한 패키지 구조**를 따른다. Fake는 테스트 소스 내에 배치한다.

```
src/test/java/com/loopers/
├── domain/
│   ├── order/
│   │   ├── OrderTest.java                  <- Entity 단위
│   │   └── OrderServiceTest.java           <- Domain Service 단위
│   ├── product/
│   │   └── ProductTest.java
│   └── member/
│       └── ...
├── application/
│   └── order/
│       └── OrderFacadeTest.java            <- Application mock 테스트
├── interfaces/
│   └── order/
│       └── OrderV1ApiE2ETest.java          <- E2E
└── utils/
    ├── DatabaseCleanUp.java
    └── FakePasswordEncoder.java             <- 공통 Fake
```

---

## 7. DB 정리 전략

통합/E2E 테스트에서 테스트 간 격리를 위해 `@AfterEach`에서 DB를 정리한다.

```java
@AfterEach
void tearDown() {
    databaseCleanUp.truncateAllTables();
}
```

`truncate` 사용 이유: `@Transactional` 롤백은 `RANDOM_PORT` E2E에서 동작하지 않고, `deleteAll()`은 외래키 순서 관리가 필요하며 느리다.
