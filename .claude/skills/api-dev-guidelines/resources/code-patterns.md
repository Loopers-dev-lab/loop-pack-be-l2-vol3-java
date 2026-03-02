# Code Patterns

## ApiResponse 패턴

모든 API 응답을 래핑하는 record.

```java
public record ApiResponse<T>(Metadata meta, T data) {
    public record Metadata(Result result, String errorCode, String message) {
        public enum Result { SUCCESS, FAIL }
        public static Metadata success() { return new Metadata(Result.SUCCESS, null, null); }
        public static Metadata fail(String errorCode, String errorMessage) { ... }
    }
    public static ApiResponse<Object> success() { ... }
    public static <T> ApiResponse<T> success(T data) { ... }
    public static ApiResponse<Object> fail(String errorCode, String errorMessage) { ... }
}
```

## Entity 패턴

```java
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Embedded
    private LoginId loginId;

    @Column(name = "password", nullable = false)
    private String password;

    // JPA용 기본 생성자 (protected)
    protected User() {}

    // private 생성자
    private User(LoginId loginId, String encodedPassword, ...) {
        this.loginId = loginId;
        this.password = encodedPassword;
    }

    // static 팩토리 메서드 (유일한 생성 경로)
    public static User create(LoginId loginId, String encodedPassword, ...) {
        return new User(loginId, encodedPassword, ...);
    }

    // 상태 변경 메서드
    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    // getter 직접 작성 (Lombok 금지)
    public LoginId getLoginId() { return this.loginId; }
}
```

**핵심 규칙:**
- `BaseEntity` 상속 (id, createdAt, updatedAt, deletedAt 제공)
- 생성자는 `private`, `static create()` 팩토리만 노출
- `protected` 기본 생성자 필수 (JPA)
- getter 직접 작성

## Value Object 패턴

```java
@Embeddable
public class BirthDate {
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final int MIN_AGE = 14;

    @Column(name = "birth_date")
    private LocalDate value;

    protected BirthDate() {} // JPA용

    public BirthDate(String rawValue) {
        validateNotBlank(rawValue);
        this.value = parseDate(rawValue);
        validateRange(this.value);
    }

    // 검증 실패 시 CoreException throw (cause 보존 필수)
    private static LocalDate parseDate(String rawValue) {
        try {
            return LocalDate.parse(rawValue);
        } catch (DateTimeParseException e) {
            throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
                    "생년월일은 YYYY-MM-DD 형식이어야 합니다.", e);
        }
    }

    public LocalDate getValue() { return this.value; }
}
```

**핵심 규칙:**
- `@Embeddable` + `@Column`으로 매핑
- 생성자에서 자체 검증, 실패 시 `CoreException` throw
- `protected` 기본 생성자 필수 (JPA)
- 상수는 `static final`로 관리

## 중첩 Record DTO 패턴

### Request DTO
```java
public class UserRequest {
    public record SignupRequest(String loginId, String password, String name,
                                String birthDate, String email) {
        @Override
        public String toString() { // 민감 필드 마스킹
            return "SignupRequest[loginId=" + loginId + ", password=*****, ...]";
        }
    }
    public record ChangePasswordRequest(String currentPassword, String newPassword) {
        @Override
        public String toString() {
            return "ChangePasswordRequest[currentPassword=*****, newPassword=*****]";
        }
    }
}
```

### Response DTO
```java
public class UserResponse {
    public record SignupResponse(String loginId, String name,
                                 LocalDate birthDate, String email) {
        // Application DTO에서 변환하는 static factory
        public static SignupResponse from(UserInfo info) {
            return new SignupResponse(info.loginId(), info.name(),
                    info.birthDate(), info.email());
        }
    }
}
```

### Application DTO (Info)
```java
public record UserInfo(String loginId, String name, String maskedName,
                       LocalDate birthDate, String email) {
    // Entity에서 변환하는 static factory
    public static UserInfo from(User user) {
        return new UserInfo(
                user.getLoginId().getValue(),
                user.getName().getValue(),
                user.getName().getMaskedValue(),
                user.getBirthDate().getValue(),
                user.getEmail().getValue()
        );
    }
}
```

**변환 흐름:** Entity -> Application DTO (Info) -> Response DTO

## ErrorType 패턴

```java
// 인터페이스 (공통 계약)
public interface ErrorType {
    HttpStatus getStatus();
    String getCode();
    String getMessage();
}

// 도메인별 열거형
public enum UserErrorType implements ErrorType {
    INVALID_LOGIN_ID(HttpStatus.BAD_REQUEST, "로그인 ID 형식이 올바르지 않습니다."),
    DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "인증에 실패했습니다.");

    private final HttpStatus status;
    private final String message;

    UserErrorType(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    @Override public HttpStatus getStatus() { return this.status; }
    @Override public String getCode() { return name(); } // enum 이름이 곧 코드
    @Override public String getMessage() { return this.message; }
}
```

## Repository 포트 + 어댑터 패턴

```java
// Domain Layer: 포트 (interface) - 인프라에 의존하지 않음
public interface UserRepository {
    User save(User user);
    Optional<User> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}

// Infrastructure Layer: 어댑터 (@Repository)
@Repository
public class UserRepositoryImpl implements UserRepository {
    private final UserJpaRepository userJpaRepository;

    public UserRepositoryImpl(UserJpaRepository userJpaRepository) {
        this.userJpaRepository = userJpaRepository;
    }

    @Override
    public User save(User user) {
        return this.userJpaRepository.save(user);
    }
}

// Infrastructure Layer: Spring Data JPA
public interface UserJpaRepository extends JpaRepository<User, Long> {
    Optional<User> findByLoginIdValue(String loginId);
}
```

## Service 패턴

```java
@Component
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncryptor passwordEncryptor;

    public UserService(UserRepository userRepository, PasswordEncryptor passwordEncryptor) {
        this.userRepository = userRepository;
        this.passwordEncryptor = passwordEncryptor;
    }

    @Transactional
    public User createUser(...) {
        // 1. VO 생성 (형식 검증)
        // 2. 교차 검증 (비즈니스 규칙)
        // 3. 중복 확인
        // 4. 처리 (암호화 + 저장)
    }

    @Transactional(readOnly = true)
    public User authenticateUser(...) { ... }
}
```

**핵심:** `@Component` 사용, 생성자 주입 + `this.` 접두어, 조회는 `readOnly = true`

## Controller 패턴

```java
@RestController
@RequestMapping("/api/v1/users")
public class UserController implements UserApiSpec {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Override
    public ApiResponse<UserResponse.SignupResponse> createUser(
            @RequestBody UserRequest.SignupRequest request) {
        User user = this.userService.createUser(...);
        UserInfo info = UserInfo.from(user);          // Entity -> Application DTO
        return ApiResponse.success(
                UserResponse.SignupResponse.from(info) // Application DTO -> Response DTO
        );
    }
}
```

## ApiSpec 인터페이스 패턴

```java
@Tag(name = "User API", description = "사용자 관련 API")
public interface UserApiSpec {
    @Operation(summary = "회원가입")
    ApiResponse<UserResponse.SignupResponse> createUser(UserRequest.SignupRequest request);
}
```

Swagger 어노테이션을 인터페이스에 분리하여 Controller를 깨끗하게 유지한다.