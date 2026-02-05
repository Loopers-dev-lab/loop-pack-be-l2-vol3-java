# Week 1 - 회원 기능

## 요구사항

### 1. 회원가입

| 항목 | 내용 |
|------|------|
| 필요 정보 | 로그인 ID, 비밀번호, 이름, 생년월일, 이메일 |
| 로그인 ID | 영문 + 숫자만 허용, 중복 불가 |
| 포맷 검증 | 이름, 이메일, 생년월일 |
| 비밀번호 규칙 | 8~16자, 영문 대소문자 + 숫자 + 특수문자만 허용 |
| 비밀번호 제약 | 생년월일 포함 불가 |
| 저장 방식 | 비밀번호 암호화 저장 |

### 2. 내 정보 조회

| 항목 | 내용 |
|------|------|
| 인증 방식 | HTTP 헤더 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |
| 반환 정보 | 로그인 ID, 이름, 생년월일, 이메일 |
| 이름 마스킹 | 마지막 글자를 `*`로 마스킹 (예: 홍길동 → 홍길*) |

### 3. 비밀번호 수정

| 항목 | 내용 |
|------|------|
| 인증 방식 | HTTP 헤더 (`X-Loopers-LoginId`, `X-Loopers-LoginPw`) |
| 필요 정보 | 기존 비밀번호, 새 비밀번호 |
| 비밀번호 규칙 | 8~16자, 영문 대소문자 + 숫자 + 특수문자만 허용 |
| 비밀번호 제약 | 생년월일 포함 불가, 현재 비밀번호 사용 불가 |

---

## 기술 결정 사항

| 항목 | 결정 | 근거 |
|------|------|------|
| 비밀번호 암호화 | `spring-security-crypto` | 전체 Spring Security는 과한 의존성, crypto만 사용 |
| 인증 처리 | `HandlerMethodArgumentResolver` | 대중적, 확장성 좋음, 컨트롤러 코드 깔끔 |
| 엔티티 네이밍 | `MemberModel` | 기존 프로젝트 패턴(`ExampleModel`) 유지 |
| DTO | Record 사용 | Java 21 기본 기능, boilerplate 감소 |
| 검증 | `@Valid` 기본 어노테이션 + 서비스 레벨 검증 | 오버엔지니어링 방지 |

---

## 구현 계획 (소스 레벨)

### Phase 1: 공통 기반 구축

#### 1-1. 의존성 추가 (`apps/commerce-api/build.gradle.kts`)

```kotlin
// 비밀번호 암호화 (spring-security-crypto만 사용)
implementation("org.springframework.security:spring-security-crypto")
```

#### 1-2. MemberModel 엔티티

**파일**: `domain/member/MemberModel.java`

```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Column(nullable = false, unique = true, length = 20)
    private String loginId;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    @Column(nullable = false, length = 100)
    private String email;

    // 생성자, getter, 비밀번호 변경 메서드
}
```

**설계 근거**:
- `loginId`: unique 제약, 영문+숫자만 허용
- `password`: BCrypt 해시값 저장 (길이 제한 없음)
- `birthDate`: `LocalDate` 타입으로 날짜만 저장
- `BaseEntity` 상속으로 id, createdAt, updatedAt, deletedAt 자동 관리

#### 1-3. MemberRepository

**파일**: `domain/member/MemberRepository.java`

```java
public interface MemberRepository {
    MemberModel save(MemberModel member);
    Optional<MemberModel> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
```

#### 1-4. MemberRepositoryImpl

**파일**: `infrastructure/member/MemberRepositoryImpl.java`

```java
@RequiredArgsConstructor
@Component
public class MemberRepositoryImpl implements MemberRepository {
    private final MemberJpaRepository memberJpaRepository;

    // 구현
}
```

#### 1-5. MemberJpaRepository

**파일**: `infrastructure/member/MemberJpaRepository.java`

```java
public interface MemberJpaRepository extends JpaRepository<MemberModel, Long> {
    Optional<MemberModel> findByLoginId(String loginId);
    boolean existsByLoginId(String loginId);
}
```

#### 1-6. PasswordEncoder 설정

**파일**: `support/auth/PasswordEncoderConfig.java`

```java
@Configuration
public class PasswordEncoderConfig {
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**설계 근거**:
- `spring-security-crypto`의 `PasswordEncoder` 인터페이스 사용
- BCrypt 알고리즘 (업계 표준)

#### 1-7. PasswordValidator

**파일**: `domain/member/PasswordValidator.java`

```java
@Component
public class PasswordValidator {

    // 8~16자, 영문 대소문자 + 숫자 + 특수문자만 허용
    private static final String PASSWORD_PATTERN = "^[A-Za-z0-9!@#$%^&*()_+=-]{8,16}$";

    public void validate(String password, LocalDate birthDate) {
        // 1. 길이 및 문자 규칙 검증
        // 2. 생년월일 포함 여부 검증 (yyyyMMdd, yyMMdd 등)
    }
}
```

**검증 항목**:
- 길이: 8~16자
- 허용 문자: 영문 대소문자, 숫자, 특수문자
- 생년월일 포함 불가: `19900101`, `900101` 등 패턴 체크

#### 1-8. 인증 컴포넌트 (HandlerMethodArgumentResolver)

**파일**: `support/auth/AuthMember.java` (어노테이션)

```java
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthMember {
}
```

**파일**: `support/auth/AuthMemberResolver.java`

```java
@RequiredArgsConstructor
@Component
public class AuthMemberResolver implements HandlerMethodArgumentResolver {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(AuthMember.class);
    }

    @Override
    public Object resolveArgument(...) {
        String loginId = request.getHeader("X-Loopers-LoginId");
        String password = request.getHeader("X-Loopers-LoginPw");

        // 1. 헤더 존재 여부 검증
        // 2. 회원 조회
        // 3. 비밀번호 일치 검증
        // 4. MemberModel 반환
    }
}
```

**파일**: `support/auth/WebMvcConfig.java`

```java
@RequiredArgsConstructor
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthMemberResolver authMemberResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(authMemberResolver);
    }
}
```

---

### Phase 2: 회원가입 기능

#### 2-1. API 설계

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| Path | `/api/v1/members` |
| Request | `{ loginId, password, name, birthDate, email }` |
| Response | `201 Created` + `{ id, loginId, name, email }` |

#### 2-2. DTO

**파일**: `interfaces/api/member/MemberV1Dto.java`

```java
public class MemberV1Dto {

    public record SignUpRequest(
        @NotBlank @Pattern(regexp = "^[A-Za-z0-9]+$") String loginId,
        @NotBlank @Size(min = 8, max = 16) String password,
        @NotBlank String name,
        @NotNull @Past LocalDate birthDate,
        @NotBlank @Email String email
    ) {}

    public record SignUpResponse(
        Long id,
        String loginId,
        String name,
        String email
    ) {
        public static SignUpResponse from(MemberInfo info) { ... }
    }
}
```

#### 2-3. 계층별 구현

**Controller** → **Facade** → **Service** → **Repository**

```
MemberV1Controller.signUp(SignUpRequest)
    ↓
MemberFacade.signUp(SignUpCommand)
    ↓
MemberService.register(SignUpCommand)
    - 로그인 ID 중복 검증
    - 비밀번호 검증 (PasswordValidator)
    - 비밀번호 암호화
    - 저장
    ↓
MemberRepository.save(MemberModel)
```

#### 2-4. TDD 사이클

| 사이클 | 테스트 케이스 | 구현 내용 |
|--------|-------------|----------|
| 1 | 정상 가입 시 201 응답 | 기본 API 흐름 구현 |
| 2 | 로그인 ID 중복 시 409 응답 | 중복 검증 로직 추가 |
| 3 | 로그인 ID 포맷 오류 시 400 응답 | @Pattern 검증 |
| 4 | 비밀번호 규칙 위반 시 400 응답 | PasswordValidator 연동 |
| 5 | 비밀번호에 생년월일 포함 시 400 응답 | 생년월일 검증 로직 |
| 6 | 이메일 포맷 오류 시 400 응답 | @Email 검증 |

---

### Phase 3: 내 정보 조회 기능

#### 3-1. API 설계

| 항목 | 내용 |
|------|------|
| Method | `GET` |
| Path | `/api/v1/members/me` |
| Headers | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| Response | `200 OK` + `{ loginId, name, birthDate, email }` |

#### 3-2. DTO

```java
public record MyInfoResponse(
    String loginId,
    String name,       // 마스킹 적용
    LocalDate birthDate,
    String email
) {
    public static MyInfoResponse from(MemberModel member) {
        return new MyInfoResponse(
            member.getLoginId(),
            maskName(member.getName()),
            member.getBirthDate(),
            member.getEmail()
        );
    }

    private static String maskName(String name) {
        if (name == null || name.length() < 2) return name;
        return name.substring(0, name.length() - 1) + "*";
    }
}
```

#### 3-3. 계층별 구현

```
MemberV1Controller.getMyInfo(@AuthMember MemberModel member)
    ↓
MyInfoResponse.from(member)  // 직접 변환 (Facade 생략 가능)
```

**설계 근거**: 단순 조회이므로 Facade 없이 Controller에서 직접 DTO 변환

#### 3-4. TDD 사이클

| 사이클 | 테스트 케이스 | 구현 내용 |
|--------|-------------|----------|
| 1 | 정상 조회 시 200 응답 | 기본 API 흐름 |
| 2 | 이름 마스킹 검증 | maskName 로직 |
| 3 | 인증 헤더 없음 시 401 응답 | AuthMemberResolver 예외 처리 |
| 4 | 잘못된 비밀번호 시 401 응답 | 비밀번호 검증 |

---

### Phase 4: 비밀번호 수정 기능

#### 4-1. API 설계

| 항목 | 내용 |
|------|------|
| Method | `PATCH` |
| Path | `/api/v1/members/me/password` |
| Headers | `X-Loopers-LoginId`, `X-Loopers-LoginPw` |
| Request | `{ currentPassword, newPassword }` |
| Response | `200 OK` |

#### 4-2. DTO

```java
public record ChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 8, max = 16) String newPassword
) {}
```

#### 4-3. 계층별 구현

```
MemberV1Controller.changePassword(@AuthMember MemberModel member, ChangePasswordRequest)
    ↓
MemberFacade.changePassword(member, ChangePasswordCommand)
    ↓
MemberService.changePassword(member, currentPassword, newPassword)
    - 현재 비밀번호 일치 검증
    - 새 비밀번호 규칙 검증
    - 새 비밀번호 ≠ 현재 비밀번호 검증
    - 비밀번호 암호화 후 업데이트
```

#### 4-4. TDD 사이클

| 사이클 | 테스트 케이스 | 구현 내용 |
|--------|-------------|----------|
| 1 | 정상 수정 시 200 응답 | 기본 API 흐름 |
| 2 | 현재 비밀번호 불일치 시 400 응답 | 비밀번호 검증 |
| 3 | 새 비밀번호 규칙 위반 시 400 응답 | PasswordValidator |
| 4 | 새 비밀번호 = 현재 비밀번호 시 400 응답 | 동일 비밀번호 검증 |
| 5 | 새 비밀번호에 생년월일 포함 시 400 응답 | 생년월일 검증 |

---

## 브랜치 전략

```
main
 └── week1
      ├── feature/sign-up (Phase 1 + Phase 2)
      ├── feature/my-info (Phase 3)
      └── feature/change-password (Phase 4)
```

---

## 패키지 구조 (최종)

```
com.loopers
├── application/member/
│   ├── MemberFacade.java
│   └── MemberInfo.java
├── domain/member/
│   ├── MemberModel.java
│   ├── MemberService.java
│   ├── MemberRepository.java
│   └── PasswordValidator.java
├── infrastructure/member/
│   ├── MemberJpaRepository.java
│   └── MemberRepositoryImpl.java
├── interfaces/api/member/
│   ├── MemberV1Controller.java
│   ├── MemberV1ApiSpec.java
│   └── MemberV1Dto.java
└── support/
    └── auth/
        ├── AuthMember.java
        ├── AuthMemberResolver.java
        ├── PasswordEncoderConfig.java
        └── WebMvcConfig.java
```

---

## ErrorType 추가 (필요시)

```java
// 기존 ErrorType에 추가
UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Unauthorized", "인증이 필요합니다."),
DUPLICATE_LOGIN_ID(HttpStatus.CONFLICT, "Duplicate Login ID", "이미 존재하는 로그인 ID입니다."),
INVALID_PASSWORD(HttpStatus.BAD_REQUEST, "Invalid Password", "비밀번호 규칙에 맞지 않습니다."),
PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "Password Mismatch", "비밀번호가 일치하지 않습니다."),
```
