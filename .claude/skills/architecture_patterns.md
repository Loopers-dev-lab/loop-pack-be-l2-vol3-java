# 아키텍처 패턴

## 레이어드 아키텍처

### 전체 구조
```
┌─────────────────────────────────────────┐
│         Interfaces Layer                │  ← 외부 통신
│  (Controller, ApiSpec, Dto, Response)   │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│        Application Layer                │  ← 유스케이스 조합
│         (Facade, Info)                  │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│          Domain Layer                   │  ← 핵심 비즈니스 로직
│  (Model, Reader, Service, Repository, VO) │
└─────────────────────────────────────────┘
              ↓
┌─────────────────────────────────────────┐
│      Infrastructure Layer               │  ← 외부 시스템 연동
│  (RepositoryImpl, JpaRepository, etc)   │
└─────────────────────────────────────────┘
```

### 의존성 규칙
- **상위 레이어 → 하위 레이어**: 의존 가능
- **하위 레이어 → 상위 레이어**: 의존 불가
- **Domain Layer**: 다른 레이어에 의존하지 않음 (순수 비즈니스 로직)
- **Infrastructure Layer**: Domain Layer의 인터페이스 구현

## Domain Layer (도메인 계층)

### 책임
- 핵심 비즈니스 로직
- 도메인 규칙 및 제약사항
- 엔티티 및 Value Object 관리

### 구성 요소

#### 1. Model (Entity)
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {
    private static final Pattern PASSWORD_PATTERN = Pattern.compile(
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?]).{8,16}$"
    );

    @Getter
    @Convert(converter = MemberIdConverter.class)
    @Column(nullable = false, unique = true, length = 10)
    private MemberId memberId;

    @Getter
    @Column(nullable = false)
    private String password;

    @Getter
    @Convert(converter = EmailConverter.class)
    @Column(length = 100)
    private Email email;

    protected MemberModel() {}

    public MemberModel(String memberId, String password, String email) {
        this.memberId = new MemberId(memberId);
        this.password = password;
        this.email = new Email(email);
    }

    public static MemberModel create(String memberId, String rawPassword, String email,
                                      String birthDate, String name, Gender gender,
                                      PasswordHasher passwordHasher) {
        validateRawPassword(rawPassword);
        validatePasswordNotContainsBirthDate(rawPassword, birthDate);
        validateGender(gender);
        String hashedPassword = passwordHasher.hash(rawPassword);
        return new MemberModel(memberId, hashedPassword, email, birthDate, name, gender);
    }

    public void matchesPassword(PasswordHasher passwordHasher, String rawPassword) {
        if (!passwordHasher.matches(rawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호가 일치하지 않습니다.");
        }
    }

    public void changePassword(String rawCurrentPassword, String newRawPassword,
                               PasswordHasher passwordHasher) {
        matchesPassword(passwordHasher, rawCurrentPassword);
        if (passwordHasher.matches(newRawPassword, this.password)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 다르게 설정해야 합니다.");
        }
        validateRawPassword(newRawPassword);
        validatePasswordNotContainsBirthDate(newRawPassword,
                this.birthDate != null ? this.birthDate.asString() : null);
        this.password = passwordHasher.hash(newRawPassword);
    }

    private static void validateRawPassword(String rawPassword) { /* ... */ }
    private static void validatePasswordNotContainsBirthDate(String rawPassword, String birthDate) { /* ... */ }
    private static void validateGender(Gender gender) { /* ... */ }
}
```

**특징**:
- JPA Entity
- `BaseEntity` 상속 (id, createdAt, updatedAt, deletedAt)
- Value Object를 필드로 사용
- 비즈니스 규칙은 Value Object에 위임
- 정적 팩토리 메서드 `create()`로 생성 시 검증 + 암호화 캡슐화
- `matchesPassword()`로 비밀번호 검증 위임

#### 2. Value Object
```java
public record MemberId(String value) {
    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    public MemberId {
        if (value == null || value.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "memberId가 비어 있습니다");
        }
        value = value.trim();

        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, 
                "memberId는 영문+숫자, 1~10자로 이루어져야 합니다: " + value);
        }
    }
}
```

**특징**:
- `record` 타입 (불변)
- Compact Constructor에서 유효성 검증
- 비즈니스 규칙 캡슐화
- 도메인 개념 표현

#### 3. Reader (읽기 전용 도메인 컴포넌트)
```java
@Component
@RequiredArgsConstructor
public class MemberReader {
    private final MemberRepository memberRepository;

    @Transactional(readOnly = true)
    public MemberModel getMemberByMemberId(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public MemberModel getOrThrow(String memberId) {
        return memberRepository.findByMemberId(new MemberId(memberId))
                .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "해당 ID의 회원이 존재하지 않습니다."));
    }

    @Transactional(readOnly = true)
    public boolean existsByMemberId(String memberId) {
        return memberRepository.existsByMemberId(new MemberId(memberId));
    }
}
```

**특징**:
- 읽기 전용 조회 로직 캡슐화
- VO 변환(`new MemberId(memberId)`)을 한 곳에서 관리
- 조회 + 예외 처리를 통합 (`getOrThrow`)
- Service와 Repository 사이의 중간 계층

#### 4. Service
```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final MemberReader memberReader;
    private final PasswordHasher passwordHasher;

    @Transactional
    public MemberModel register(String memberId, String rawPassword, String email,
                                 String birthDate, String name, Gender gender) {
        if (memberReader.existsByMemberId(memberId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
        }

        MemberModel member = MemberModel.create(memberId, rawPassword, email, birthDate, name, gender, passwordHasher);
        return memberRepository.save(member);
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String loginPw) {
        MemberModel member = memberReader.getOrThrow(loginId);
        member.matchesPassword(passwordHasher, loginPw);
        return member;
    }

    @Transactional
    public void changePassword(String loginId, String loginPw,
                               String currentPassword, String newPassword) {
        MemberModel member = memberReader.getOrThrow(loginId);
        member.matchesPassword(passwordHasher, loginPw);
        member.changePassword(currentPassword, newPassword, passwordHasher);
    }
}
```

**특징**:
- 도메인 간 조율 및 트랜잭션 관리
- MemberReader를 통한 조회 (읽기 전용 분리)
- MemberModel.create()에 생성 검증 위임 (단일 엔티티 규칙은 모델이 담당)
- 중복 체크 등 교차 엔티티 규칙만 Service에서 관리
- changePassword()는 JPA dirty checking으로 자동 반영
- Repository 인터페이스 사용 (구현체 의존 X)

#### 4. Repository Interface
```java
public interface MemberRepository {
    MemberModel save(MemberModel member);
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
```

**특징**:
- 도메인 계층에 위치
- 구현체는 Infrastructure 계층
- 도메인 용어 사용
- 기술 세부사항 숨김

## Application Layer (응용 계층)

### 책임
- 여러 도메인 서비스 조합
- 유스케이스 구현
- 트랜잭션 경계 설정 (선택적)

### 구성 요소

#### 1. Facade
```java
@Component
@RequiredArgsConstructor
public class MemberFacade {
    private final MemberService memberService;
    private final PointService pointService;
    private final NotificationService notificationService;

    @Transactional
    public MemberInfo registerMemberWithWelcomePoint(MemberInfo.RegisterRequest request) {
        // 1. 회원 가입
        MemberModel member = memberService.register(
            request.memberId(),
            request.password(),
            request.email(),
            request.birthDate(),
            request.name(),
            request.gender()
        );

        // 2. 웰컴 포인트 지급
        pointService.grantWelcomePoint(member.getMemberId());

        // 3. 가입 환영 알림 발송
        notificationService.sendWelcomeNotification(member.getEmail());

        return MemberInfo.from(member);
    }
}
```

**특징**:
- 여러 Service 조합
- 복잡한 유스케이스 처리
- 트랜잭션 경계 관리
- Info 객체 반환

#### 2. Info (응용 계층 DTO)
```java
public class MemberInfo {
    public record RegisterRequest(
        String memberId,
        String password,
        String email,
        String birthDate,
        String name,
        Gender gender
    ) {}

    public record MemberDetail(
        Long id,
        String memberId,
        String email,
        String birthDate,
        String name,
        Gender gender
    ) {
        public static MemberDetail from(MemberModel member) {
            return new MemberDetail(
                member.getId(),
                member.getMemberId().value(),
                member.getEmail().address(),
                member.getBirthDate().asString(),
                member.getName().value(),
                member.getGender()
            );
        }
    }
}
```

**특징**:
- 응용 계층 내부 DTO
- 도메인 모델 → Info 변환
- Interfaces 계층과 분리

## Interfaces Layer (인터페이스 계층)

### 책임
- 외부 요청 수신 및 응답
- 입력 검증 (형식, 필수값)
- DTO ↔ Domain 변환
- HTTP 상태 코드 관리

### 구성 요소

#### 1. Controller
```java
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/members")
public class MemberV1Controller implements MemberV1ApiSpec {

    private final MemberService memberService;

    @PostMapping("/register")
    @Override
    public ApiResponse<MemberV1Dto.MemberResponse> register(
            @Valid @RequestBody MemberV1Dto.RegisterRequest request) {
        MemberModel member = memberService.register(
            request.memberId(),
            request.password(),
            request.email(),
            request.birthDate(),
            request.name(),
            request.gender()
        );

        MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(member);
        return ApiResponse.success(response);
    }
}
```

**특징**:
- REST API 엔드포인트
- ApiSpec 인터페이스 구현 (Swagger)
- Service 또는 Facade 호출
- ApiResponse 반환

#### 2. ApiSpec (Swagger 명세)
```java
@Tag(name = "회원 관리 API", description = "회원 관련 API")
public interface MemberV1ApiSpec {
    @Operation(
        summary = "회원 등록",
        description = "새로운 회원을 등록합니다."
    )
    ApiResponse<MemberV1Dto.MemberResponse> register(
        @Schema(name = "회원 등록 요청 DTO", description = "회원 등록에 필요한 정보를 담고 있는 DTO")
        @Valid @RequestBody MemberV1Dto.RegisterRequest request
    );
}
```

**특징**:
- Swagger 문서화
- API 명세 분리
- Controller는 구현만 담당

#### 3. Dto
```java
public class MemberV1Dto {
    public record RegisterRequest(
        @NotBlank String memberId,
        @NotBlank String password,
        @NotBlank String email,
        @NotBlank String birthDate,
        @NotBlank String name,
        @NotNull Gender gender
    ) {}

    public record MemberResponse(
        Long id,
        String memberId,
        String email,
        String birthDate,
        String name,
        Gender gender
    ) {
        public static MemberResponse from(MemberModel member) {
            return new MemberResponse(
                member.getId(),
                member.getMemberId().value(),
                member.getEmail().address(),
                member.getBirthDate().asString(),
                member.getName().value(),
                member.getGender()
            );
        }
    }
}
```

**특징**:
- `record` 타입 (불변)
- Jakarta Validation
- 정적 팩토리 메서드 (`from`)
- 버전별 DTO 분리

#### 4. ApiResponse
```java
public record ApiResponse<T>(Metadata meta, T data) {
    public record Metadata(Result result, String errorCode, String message) {
        public enum Result {
            SUCCESS, FAIL
        }

        public static Metadata success() {
            return new Metadata(Result.SUCCESS, null, null);
        }

        public static Metadata fail(String errorCode, String errorMessage) {
            return new Metadata(Result.FAIL, errorCode, errorMessage);
        }
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(Metadata.success(), data);
    }

    public static ApiResponse<Object> fail(String errorCode, String errorMessage) {
        return new ApiResponse<>(Metadata.fail(errorCode, errorMessage), null);
    }
}
```

**특징**:
- 표준 응답 포맷
- 성공/실패 구분
- 에러 코드 및 메시지 포함

#### 5. ApiControllerAdvice
```java
@RestControllerAdvice
@Slf4j
public class ApiControllerAdvice {
    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(CoreException e) {
        log.warn("CoreException : {}", e.getCustomMessage() != null ? e.getCustomMessage() : e.getMessage(), e);
        return failureResponse(e.getErrorType(), e.getCustomMessage());
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handleBadRequest(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
            .map(error -> String.format("필드 '%s'의 값 '%s'이(가) 잘못되었습니다.", 
                error.getField(), error.getRejectedValue()))
            .collect(Collectors.joining(", "));
        return failureResponse(ErrorType.BAD_REQUEST, message);
    }

    @ExceptionHandler
    public ResponseEntity<ApiResponse<?>> handle(Throwable e) {
        log.error("Exception : {}", e.getMessage(), e);
        return failureResponse(ErrorType.INTERNAL_ERROR, null);
    }

    private ResponseEntity<ApiResponse<?>> failureResponse(ErrorType errorType, String errorMessage) {
        return ResponseEntity.status(errorType.getStatus())
            .body(ApiResponse.fail(errorType.getCode(), 
                errorMessage != null ? errorMessage : errorType.getMessage()));
    }
}
```

**특징**:
- 전역 예외 처리
- 일관된 에러 응답
- 로깅

## Infrastructure Layer (인프라 계층)

### 책임
- 외부 시스템 연동
- 기술적 세부사항 구현
- Domain Repository 구현

### 구성 요소

#### 1. RepositoryImpl
```java
@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    @Override
    public MemberModel save(MemberModel member) {
        return memberJpaRepository.save(member);
    }

    @Override
    public boolean existsByMemberId(MemberId memberId) {
        return memberJpaRepository.existsByMemberId(memberId);
    }

    @Override
    public Optional<MemberModel> findByMemberId(MemberId memberId) {
        return memberJpaRepository.findByMemberId(memberId);
    }
}
```

**특징**:
- Domain Repository 구현
- JpaRepository 위임
- 기술 세부사항 숨김

#### 2. JpaRepository
```java
public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    boolean existsByMemberId(MemberId memberId);
    Optional<MemberModel> findByMemberId(MemberId memberId);
}
```

**특징**:
- Spring Data JPA
- 쿼리 메서드
- 기본 CRUD 제공

#### 3. Converter
```java
@Converter(autoApply = false)
public class MemberIdConverter implements AttributeConverter<MemberId, String> {

    @Override
    public String convertToDatabaseColumn(MemberId attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public MemberId convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new MemberId(dbData);
    }
}
```

**특징**:
- Value Object ↔ DB 타입 변환
- null-safety
- `autoApply = false` (명시적 사용)

## 패키지 구조

### commerce-api 모듈
```
com.loopers
├── domain                          # 도메인 계층
│   ├── member
│   │   ├── MemberModel.java       # Entity
│   │   ├── MemberReader.java      # Reader (읽기 전용 컴포넌트)
│   │   ├── MemberService.java     # Service
│   │   ├── MemberRepository.java  # Repository Interface
│   │   ├── MemberId.java          # Value Object
│   │   ├── Email.java             # Value Object
│   │   ├── BirthDate.java         # Value Object
│   │   ├── Name.java              # Value Object
│   │   ├── Gender.java            # Enum
│   │   └── PasswordHasher.java    # Interface
│   └── order
│       └── ...
├── application                     # 응용 계층
│   ├── member
│   │   ├── MemberFacade.java      # Facade
│   │   └── MemberInfo.java        # Info
│   └── order
│       └── ...
├── infrastructure                  # 인프라 계층
│   ├── member
│   │   ├── MemberRepositoryImpl.java
│   │   └── MemberJpaRepository.java
│   ├── security
│   │   └── BCryptPasswordHasher.java
│   └── jpa
│       └── converter
│           ├── MemberIdConverter.java
│           ├── EmailConverter.java
│           └── ...
├── interfaces                      # 인터페이스 계층
│   └── api
│       ├── member
│       │   ├── MemberV1Controller.java
│       │   ├── MemberV1ApiSpec.java
│       │   └── MemberV1Dto.java
│       ├── ApiResponse.java
│       └── ApiControllerAdvice.java
└── support                         # 공통 지원
    └── error
        ├── CoreException.java
        └── ErrorType.java
```

## 레이어 간 데이터 흐름

### 요청 흐름 (Request Flow)
```
Client Request
    ↓
Controller (Interfaces)
    ↓ Dto → Domain
Service/Facade (Domain/Application)
    ↓
Repository (Domain Interface)
    ↓
RepositoryImpl (Infrastructure)
    ↓
JpaRepository (Infrastructure)
    ↓
Database
```

### 응답 흐름 (Response Flow)
```
Database
    ↓
JpaRepository → Entity
    ↓
RepositoryImpl → Entity
    ↓
Repository → Entity
    ↓
Service/Facade → Entity
    ↓ Entity → Dto
Controller → ApiResponse<Dto>
    ↓
Client Response
```

## 설계 원칙

### 1. 단일 책임 원칙 (SRP)
- 각 클래스는 하나의 책임만
- Controller: 요청/응답 처리
- Service: 비즈니스 로직
- Repository: 데이터 접근

### 2. 개방-폐쇄 원칙 (OCP)
- 확장에는 열려있고 수정에는 닫혀있음
- 인터페이스 기반 설계
- Repository 인터페이스 → 구현체 교체 가능

### 3. 리스코프 치환 원칙 (LSP)
- 하위 타입은 상위 타입을 대체 가능
- BaseEntity 상속 구조

### 4. 인터페이스 분리 원칙 (ISP)
- 클라이언트는 사용하지 않는 인터페이스에 의존하지 않음
- Repository 인터페이스 최소화

### 5. 의존성 역전 원칙 (DIP)
- 고수준 모듈은 저수준 모듈에 의존하지 않음
- Domain → Repository Interface
- Infrastructure → Repository 구현

## 트랜잭션 관리

### Service 계층
```java
@Service
@RequiredArgsConstructor
public class MemberService {
    @Transactional
    public MemberModel register(/* ... */) {
        // 중복 체크 + MemberModel.create() + save
    }

    @Transactional(readOnly = true)
    public MemberModel authenticate(String loginId, String loginPw) {
        // 조회 + 비밀번호 검증
    }
}
```

### Facade 계층 (여러 Service 조합)
```java
@Component
@RequiredArgsConstructor
public class MemberFacade {
    @Transactional
    public MemberInfo registerMemberWithWelcomePoint(/* ... */) {
        // 여러 Service 호출을 하나의 트랜잭션으로
        memberService.register(/* ... */);
        pointService.grantWelcomePoint(/* ... */);
        return MemberInfo.from(member);
    }
}
```

## 예외 처리 전략

### 1. 도메인 예외
```java
// Value Object에서 발생
public record MemberId(String value) {
    public MemberId {
        if (!PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 memberId");
        }
    }
}
```

### 2. 비즈니스 예외
```java
// Model 정적 팩토리에서 발생 (단일 엔티티 규칙)
public static MemberModel create(/* ... */) {
    validateRawPassword(rawPassword);     // 비밀번호 형식 검증
    validateGender(gender);               // 필수값 검증
    // ...
}

// Service에서 발생 (교차 엔티티 규칙)
public MemberModel register(/* ... */) {
    if (memberReader.existsByMemberId(memberId)) {
        throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
    }
}
```

### 3. 전역 예외 처리
```java
// ApiControllerAdvice에서 일괄 처리
@ExceptionHandler
public ResponseEntity<ApiResponse<?>> handle(CoreException e) {
    return failureResponse(e.getErrorType(), e.getCustomMessage());
}
```

## 모범 사례

### ✅ Do
1. **레이어 분리 준수**: 각 레이어의 책임 명확히
2. **인터페이스 기반 설계**: Repository는 인터페이스로
3. **Value Object 활용**: 도메인 개념 캡슐화
4. **불변 객체 선호**: record, final 활용
5. **명확한 네이밍**: 레이어별 접미사 사용

### ❌ Don't
1. **레이어 건너뛰기**: Controller → Repository 직접 호출 금지
2. **순환 참조**: 레이어 간 순환 의존성 금지
3. **도메인 로직 누수**: Controller에 비즈니스 로직 작성 금지
4. **기술 세부사항 노출**: Domain에서 JPA 어노테이션 최소화
5. **God Service**: 하나의 Service에 모든 로직 집중 금지
