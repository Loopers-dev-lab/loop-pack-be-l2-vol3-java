---
name: test-code-guidelines
description: "테스트 코드 작성 규칙, TDD Red-Green-Refactor 워크플로우, 테스트 더블 가이드. 테스트를 작성하거나 리팩토링할 때 활성화한다."
---

테스트 코드를 작성할 때 반드시 아래 규칙을 따른다.

## 핵심 규칙 (반드시 준수)

### 1. DisplayName 일관성
- 모든 테스트 클래스에 `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)` 필수
- 메서드명은 **한글**로 작성. 언더스코어(`_`)가 공백으로 치환됨
- **말도 안 되는 영문 사용 금지**. 한글로 명확하게 의도를 드러낸다

```java
// Good
void 존재하지_않는_사용자로_조회하면_예외가_발생한다() {}
void 비밀번호가_8자_미만이면_검증에_실패한다() {}

// Bad
void testCreateUser() {}
void shouldReturnErrorWhenUserNotFound() {}
```

### 2. 실패 먼저, 해피 패스 나중 (Fail-first ordering)
- **실패 케이스를 먼저 작성**하고 해피 케이스를 나중에 작성한다
- `@Nested` 클래스 내에서도 실패 테스트가 위에, 성공 테스트가 아래에 위치한다

```java
@Nested
@DisplayName("회원가입 시")
class CreateUser {
    // 실패 케이스 먼저
    @Test void 로그인_ID가_중복이면_예외가_발생한다() {}
    @Test void 비밀번호가_정책에_부합하지_않으면_예외가_발생한다() {}
    @Test void 생년월일이_비밀번호에_포함되면_예외가_발생한다() {}

    // 해피 케이스 나중에
    @Test void 유효한_정보면_정상적으로_가입된다() {}
}
```

### 3. 하나의 테스트에 검증 하나만
- **하나의 테스트 메서드에서 assert는 단 하나만** 수행한다
- `assertThat(...).isEqualTo(...)` 하나로 끝내야 한다
- 검증을 두 개 하고 싶으면 **메서드를 두 개로 분리**한다

```java
// Good: 검증 하나씩 분리
@Test void 가입_후_로그인_ID가_저장된다() {
    User user = userService.createUser("nahyeon", ...);
    assertThat(user.getLoginId().getValue()).isEqualTo("nahyeon");
}

@Test void 가입_후_비밀번호가_암호화되어_저장된다() {
    User user = userService.createUser("nahyeon", ...);
    verify(passwordEncryptor).encode("Hx7!mK2@");
}

// Bad: 하나의 메서드에서 여러 검증
@Test void 가입이_정상적으로_수행된다() {
    User user = userService.createUser("nahyeon", ...);
    assertThat(user.getLoginId().getValue()).isEqualTo("nahyeon");
    assertThat(user.getPassword()).isEqualTo("$2a$10$encodedHash");
    verify(passwordEncryptor).encode("Hx7!mK2@");
}
```

### 4. 필드 여러 개 변경 검증은 extracting 사용
- 하나의 객체에서 여러 필드가 동시에 바뀌는 것을 검증할 때는 `extracting()` 사용

```java
// Good: extracting으로 여러 필드 한 번에 검증
assertThat(user)
    .extracting(u -> u.getLoginId().getValue(), u -> u.getName().getValue())
    .containsExactly("nahyeon", "홍길동");
```

### 5. verify 검증 필수
- 테스트 대역(Mock)을 사용한 경우 **행위 검증(verify)은 필수**
- 데이터 검증(assertThat)과 행위 검증(verify)은 별도 메서드로 분리

```java
// Good: 행위 검증 전용 메서드
@Test void 가입_시_비밀번호_암호화가_수행된다() {
    userService.createUser("nahyeon", "Hx7!mK2@", ...);
    verify(passwordEncryptor).encode("Hx7!mK2@");
}

@Test void 가입_시_저장소에_저장된다() {
    userService.createUser("nahyeon", ...);
    verify(userRepository).save(any(User.class));
}
```

### 6. 검증값은 무조건 하드코딩
- assert 비교값에 **변수를 사용하지 않는다**. 기대값을 리터럴로 직접 작성한다

```java
// Good: 하드코딩
assertThat(user.getLoginId().getValue()).isEqualTo("nahyeon");

// Bad: 변수 사용
String expectedLoginId = "nahyeon";
assertThat(user.getLoginId().getValue()).isEqualTo(expectedLoginId);
```

### 7. 실패/성공 테스트 코드 분리
- 하나의 메서드 안에서 **실패 검증과 성공 검증이 겹치면 안 된다**
- 실패 케이스 메서드와 성공 케이스 메서드를 명확히 분리한다

## TDD 워크플로우

### Red Phase
- 요구사항을 만족하는 **실패 테스트 케이스**를 먼저 작성
- 3A 원칙: `// arrange` - `// act` - `// assert` 주석 사용

### Green Phase
- 테스트를 통과하는 **최소한의 코드** 작성
- 오버엔지니어링 금지

### Refactor Phase
- 코드 품질 개선, 불필요한 코드 제거
- 모든 테스트 통과 필수

## 테스트 피라미드

### Unit Test (`*Test.java`)
- 순수 JVM에서 실행 (Spring 없음)
- 의존 관계를 Mock(Test Double)으로 격리
- `Mockito.mock()` 수동 생성, `@BeforeEach`에서 SUT 조립
- 도메인 모델 (Entity, VO, Domain Service) 검증

### Integration Test (`*IntegrationTest.java`)
- `@SpringBootTest` 사용 (실제 Spring 빈 조립)
- Testcontainers로 MySQL/Redis/Kafka 실제 컨테이너
- `@AfterEach`에서 `databaseCleanUp.truncateAllTables()` 필수
- Service -> Repository -> DB 실제 비즈니스 흐름 검증

### E2E Test (`*E2ETest.java`)
- `@SpringBootTest(webEnvironment = RANDOM_PORT)` 사용
- `TestRestTemplate`으로 실제 HTTP 요청/응답 검증
- HTTP 상태 코드, 응답 구조 검증 중심
- `@AfterEach`에서 DB 초기화 필수

## 테스트 더블 역할 구분

| 역할 | 목적 | 사용 방식 |
|------|------|----------|
| **Stub** | 고정된 응답 제공 (상태 기반) | `when().thenReturn()` |
| **Mock** | 호출 여부/횟수 검증 (행위 기반) | `verify(...)` |
| **Spy** | 진짜 객체 + 일부 조작 | `spy()` + `doReturn()` |
| **Fake** | 실제처럼 동작하는 가짜 구현체 | 직접 클래스 구현 (InMemoryRepository) |

## 테스트 코드 구조 템플릿

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceTest {

    private UserRepository userRepository;
    private PasswordEncryptor passwordEncryptor;
    private UserService userService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        passwordEncryptor = mock(PasswordEncryptor.class);
        userService = new UserService(userRepository, passwordEncryptor);
    }

    @Nested
    @DisplayName("회원가입 시")
    class CreateUser {
        // 실패 케이스 먼저
        @Test
        void 이미_존재하는_로그인_ID면_예외가_발생한다() {
            // arrange
            when(userRepository.existsByLoginId("nahyeon")).thenReturn(true);

            // act & assert
            assertThatThrownBy(() -> userService.createUser("nahyeon", ...))
                    .isInstanceOf(CoreException.class);
        }

        // 해피 케이스 나중에
        @Test
        void 유효한_정보면_저장소에_저장된다() {
            // arrange
            when(userRepository.existsByLoginId(anyString())).thenReturn(false);
            when(passwordEncryptor.encode(anyString())).thenReturn("$2a$10$hash");
            when(userRepository.save(any(User.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));

            // act
            userService.createUser("nahyeon", "Hx7!mK2@", ...);

            // assert
            verify(userRepository).save(any(User.class));
        }
    }
}
```

## 금지 사항

- Mock 남발: 과도한 Mock 사용 금지. 실제 동작 검증 우선
- null-safety 위반: Optional 활용 필수
- 디버깅 코드: `System.out.println` 잔류 금지
- `assertAll` 사용 금지: 여러 검증이 필요하면 메서드를 분리한다