# 코딩 표준

## 네이밍 규칙

### 클래스 및 인터페이스

#### Entity (도메인 모델)
- **패턴**: `{Domain}Model`
- **예시**: `MemberModel`, `OrderModel`, `ProductModel`
- **타입**: `class` (가변 상태 관리)
- **위치**: `domain.{domain}`

#### Value Object
- **패턴**: `{Concept}` (접미사 없음)
- **예시**: `MemberId`, `Email`, `BirthDate`, `Name`, `Money`
- **타입**: `record` (불변 객체)
- **위치**: `domain.{domain}`

#### Reader (읽기 전용 컴포넌트)
- **패턴**: `{Domain}Reader`
- **예시**: `MemberReader`, `OrderReader`, `ProductReader`
- **위치**: `domain.{domain}`

#### Service
- **패턴**: `{Domain}Service`
- **예시**: `MemberService`, `OrderService`, `PaymentService`
- **위치**: `domain.{domain}`

#### Repository Interface
- **패턴**: `{Domain}Repository`
- **예시**: `MemberRepository`, `OrderRepository`
- **위치**: `domain.{domain}`

#### Repository Implementation
- **패턴**: `{Domain}RepositoryImpl`
- **예시**: `MemberRepositoryImpl`, `OrderRepositoryImpl`
- **위치**: `infrastructure.{domain}`

#### JPA Repository
- **패턴**: `{Domain}JpaRepository`
- **예시**: `MemberJpaRepository`, `OrderJpaRepository`
- **위치**: `infrastructure.{domain}`

#### Controller
- **패턴**: `{Domain}V{version}Controller`
- **예시**: `MemberV1Controller`, `OrderV2Controller`
- **위치**: `interfaces.api.{domain}`

#### API Spec (Swagger)
- **패턴**: `{Domain}V{version}ApiSpec`
- **예시**: `MemberV1ApiSpec`, `OrderV2ApiSpec`
- **위치**: `interfaces.api.{domain}`

#### DTO
- **패턴**: `{Domain}V{version}Dto` (내부 클래스로 Request/Response 정의)
- **예시**: `MemberV1Dto.RegisterRequest`, `MemberV1Dto.MemberResponse`
- **타입**: `record` (불변 DTO)
- **위치**: `interfaces.api.{domain}`

#### Facade
- **패턴**: `{Domain}Facade`
- **예시**: `MemberFacade`, `OrderFacade`
- **위치**: `application.{domain}`

#### Info (응용 계층 DTO)
- **패턴**: `{Domain}Info`
- **예시**: `MemberInfo`, `OrderInfo`
- **위치**: `application.{domain}`

#### Exception
- **패턴**: `{Concept}Exception`
- **예시**: `CoreException`, `BusinessException`
- **위치**: `support.error`

#### Converter (JPA)
- **패턴**: `{ValueObject}Converter`
- **예시**: `MemberIdConverter`, `EmailConverter`, `BirthDateConverter`
- **위치**: `infrastructure.jpa.converter`

### 메서드

#### Repository
- **조회**: `findBy{Condition}`, `findAllBy{Condition}`
- **존재 확인**: `existsBy{Condition}`
- **저장**: `save`, `saveAll`
- **삭제**: `delete`, `deleteBy{Condition}`
- **카운트**: `countBy{Condition}`

#### Service
- **비즈니스 로직**: 도메인 용어 사용
  - `register` (회원 가입)
  - `getMemberByMemberId` (회원 조회)
  - `updateProfile` (프로필 수정)
  - `withdraw` (회원 탈퇴)

#### Controller
- **HTTP 메서드 매핑**: RESTful 원칙
  - `GET`: 조회
  - `POST`: 생성
  - `PUT`: 전체 수정
  - `PATCH`: 부분 수정
  - `DELETE`: 삭제

### 변수
- **camelCase** 사용
- **의미 있는 이름** 사용 (약어 지양)
- **boolean**: `is`, `has`, `can` 접두사
  - 예: `isDeleted`, `hasPermission`, `canAccess`

### 상수
- **UPPER_SNAKE_CASE** 사용
- **static final** 선언
- 예: `MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT`, `API_VERSION`

## 타입 사용 규칙

### Value Object (record)
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
- `record` 타입 사용 (불변)
- Compact Constructor에서 유효성 검증
- 비즈니스 규칙 캡슐화
- null-safety 보장

### DTO (record)
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
- `record` 타입 사용 (불변)
- Jakarta Validation 어노테이션 활용
- 정적 팩토리 메서드 (`from`, `of`) 제공
- 내부 클래스로 Request/Response 그룹화

### Entity (class)
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

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
}
```

**특징**:
- `class` 타입 사용 (가변 상태)
- `BaseEntity` 상속 (id, createdAt, updatedAt, deletedAt)
- Value Object를 필드로 사용
- JPA Converter로 Value Object 매핑
- `protected` 기본 생성자 (JPA 요구사항)
- Lombok `@Getter` 사용
- 정적 팩토리 메서드 `create()`로 생성 시 검증 로직 캡슐화
- 도메인 행위 메서드 (예: `matchesPassword()`) 제공

## 의존성 주입

### 생성자 주입 (권장)
```java
@Service
@RequiredArgsConstructor
public class MemberService {
    private final MemberRepository memberRepository;
    private final PasswordHasher passwordHasher;
    
    // 비즈니스 로직
}
```

**특징**:
- `@RequiredArgsConstructor` 사용 (Lombok)
- `final` 필드로 불변성 보장
- 테스트 용이성

### 필드 주입 (지양)
```java
// ❌ 사용하지 말 것
@Autowired
private MemberRepository memberRepository;
```

## 예외 처리

### CoreException 사용
```java
public class MemberService {
    public MemberModel register(String memberId, String password) {
        if (memberRepository.existsByMemberId(new MemberId(memberId))) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 가입된 ID 입니다.");
        }
        // ...
    }
}
```

### ErrorType 정의
```java
@Getter
@RequiredArgsConstructor
public enum ErrorType {
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Server Error", "일시적인 오류가 발생했습니다."),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad Request", "잘못된 요청입니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not Found", "존재하지 않는 요청입니다."),
    CONFLICT(HttpStatus.CONFLICT, "Conflict", "이미 존재하는 리소스입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
```

### 전역 예외 처리
- `ApiControllerAdvice`에서 일괄 처리
- `CoreException` → 비즈니스 예외
- `MethodArgumentNotValidException` → 검증 실패
- `Throwable` → 예상치 못한 예외

## API 응답 구조

### 성공 응답
```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    "id": 1,
    "memberId": "testuser1",
    "email": "test@example.com"
  }
}
```

### 실패 응답
```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "Bad Request",
    "message": "이미 가입된 ID 입니다."
  },
  "data": null
}
```

### ApiResponse 사용
```java
@PostMapping("/register")
public ApiResponse<MemberV1Dto.MemberResponse> register(@Valid @RequestBody MemberV1Dto.RegisterRequest request) {
    MemberModel member = memberService.register(/* ... */);
    MemberV1Dto.MemberResponse response = MemberV1Dto.MemberResponse.from(member);
    return ApiResponse.success(response);
}
```

## JPA 관련

### BaseEntity
- 모든 Entity는 `BaseEntity` 상속
- `id`, `createdAt`, `updatedAt`, `deletedAt` 자동 관리
- Soft Delete 지원 (`delete()`, `restore()`)
- `guard()` 메서드로 유효성 검증 (PrePersist, PreUpdate)

### JPA Converter
```java
@Converter(autoApply = false)
public class EmailConverter implements AttributeConverter<Email, String> {

    @Override
    public String convertToDatabaseColumn(Email attribute) {
        return attribute == null ? null : attribute.address();
    }

    @Override
    public Email convertToEntityAttribute(String dbData) {
        return dbData == null ? null : new Email(dbData);
    }
}
```

**특징**:
- Value Object ↔ DB 타입 변환
- null-safety 보장
- `autoApply = false` (명시적 사용)

### Entity 사용
```java
@Convert(converter = EmailConverter.class)
@Column(length = 100)
private Email email;
```

## 트랜잭션

### @Transactional 사용
```java
@Service
@RequiredArgsConstructor
public class MemberService {

    @Transactional
    public MemberModel register(/* ... */) {
        // 쓰기 작업
    }

    @Transactional(readOnly = true)
    public MemberModel getMemberByMemberId(String memberId) {
        // 읽기 전용 작업
    }
}
```

**규칙**:
- Service 계층에 적용
- 읽기 전용: `@Transactional(readOnly = true)`
- 쓰기 작업: `@Transactional`
- Repository 계층에는 적용하지 않음 (Service에서 관리)

## Null Safety

### Optional 사용
```java
public interface MemberRepository {
    Optional<MemberModel> findByMemberId(MemberId memberId);
}

// Service에서 사용
public MemberModel getMemberByMemberId(String memberId) {
    return memberRepository.findByMemberId(new MemberId(memberId))
            .orElse(null);  // 또는 .orElseThrow()
}
```

### Value Object에서 null 검증
```java
public record Email(String address) {
    public Email {
        if (address == null || address.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "email이 비어 있습니다");
        }
        // ...
    }
}
```

## 코드 스타일

### Import 순서
1. Java 표준 라이브러리
2. 외부 라이브러리
3. Spring Framework
4. 프로젝트 내부 패키지

### 줄바꿈
- 한 줄 최대 120자
- 메서드 체이닝: 각 메서드마다 줄바꿈

### 주석
- 코드로 설명 가능한 경우 주석 지양
- 복잡한 비즈니스 로직: 간단한 설명 추가
- JavaDoc: public API에만 작성

### Lombok 사용
- `@Getter`: Entity, DTO
- `@RequiredArgsConstructor`: Service, Controller
- `@Slf4j`: 로깅이 필요한 클래스
- `@Builder`: 복잡한 객체 생성 (선택적)

## 금지 사항

### ❌ Never Do
1. **println 사용 금지**: 로거 사용 (`@Slf4j`)
2. **null 반환 지양**: `Optional` 사용
3. **Magic Number**: 상수로 정의
4. **God Class**: 단일 책임 원칙 준수
5. **Unused Import**: 사용하지 않는 import 제거
6. **Raw Type**: 제네릭 타입 명시
7. **Exception Swallowing**: 예외를 무시하지 말 것

### ✅ Best Practices
1. **불변 객체 선호**: `record`, `final` 활용
2. **명확한 네이밍**: 의도가 드러나는 이름
3. **작은 메서드**: 한 가지 일만 수행
4. **Early Return**: 중첩 if 문 지양
5. **Stream API**: 컬렉션 처리 시 활용
6. **정적 팩토리 메서드**: 생성자 대신 사용 고려
