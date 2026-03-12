# Member 기능 구현 계획

## 요구사항 상세

### 회원가입
- **필요 정보**: loginId, password, name, birthDate, email
- 이미 가입된 loginId로는 가입 불가
- 각 정보 포맷 검증 필요:
  - **loginId**: 영문과 숫자만 허용
  - **name**: 필수값
  - **email**: 이메일 형식
  - **birthDate**: 날짜 형식
- 비밀번호는 암호화해 저장

### 비밀번호 규칙
- 8~16자
- 영문 대소문자, 숫자, 특수문자만 허용
- 생년월일 포함 불가 (YYYYMMDD, YYMMDD 형식 모두)
- (비밀번호 변경 시) 현재 비밀번호 재사용 불가

### 내 정보 조회
- **인증 방식**: 헤더로 전달
  - `X-Loopers-LoginId`: 로그인 ID
  - `X-Loopers-LoginPw`: 비밀번호
- **반환 정보**: loginId, name, birthDate, email
- **마스킹**: 이름의 마지막 글자를 `*`로 마스킹
  - 예: "홍길동" → "홍길*"

### 비밀번호 수정
- **필요 정보**: 기존 비밀번호, 새 비밀번호
- 기존 비밀번호 일치 확인 필수
- 비밀번호 규칙 적용 + 현재 비밀번호 재사용 불가

---

## API 설계

| 기능 | Method | Endpoint | 인증 |
|------|--------|----------|------|
| 회원가입 | POST | `/api/v1/members` | 불필요 |
| 내정보조회 | GET | `/api/v1/members/me` | 헤더 인증 |
| 비밀번호변경 | PATCH | `/api/v1/members/me/password` | 헤더 인증 |

### Request/Response 예시

#### 회원가입
```http
POST /api/v1/members
Content-Type: application/json

{
  "loginId": "testuser",
  "password": "Test1234!",
  "name": "홍길동",
  "birthDate": "1990-01-15",
  "email": "test@example.com"
}
```

#### 내 정보 조회
```http
GET /api/v1/members/me
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Test1234!
```
```json
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "loginId": "testuser",
    "name": "홍길*",
    "birthDate": "1990-01-15",
    "email": "test@example.com"
  }
}
```

#### 비밀번호 변경
```http
PATCH /api/v1/members/me/password
X-Loopers-LoginId: testuser
X-Loopers-LoginPw: Test1234!
Content-Type: application/json

{
  "currentPassword": "Test1234!",
  "newPassword": "NewPass5678!"
}
```

---

## TDD 테스트 작성 전략

### 원칙: "가장 단순한 것" 또는 "가장 예외적인 것"부터

TDD에서 테스트 순서를 정하는 두 가지 접근법:

| 접근법 | 설명 | 장점 |
|--------|------|------|
| **Simplest First** | 가장 단순한 성공 케이스부터 | 빠르게 동작하는 코드 확보 |
| **Edge First** | 가장 예외적인/경계 케이스부터 | 견고한 검증 로직 먼저 확보 |

### 권장: 혼합 전략 (Zombie 방법론)

```
Z - Zero (빈 값, null)
O - One (단일 값, 정상 케이스 하나)
M - Many (여러 값, 경계값)
B - Boundary (경계 조건)
I - Interface (입출력 형식)
E - Exception (예외 상황)
```

**실제 적용 순서:**
1. **Zero/Null** → 가장 단순한 예외 (null, 빈 값)
2. **One** → 정상 동작 하나
3. **Boundary** → 경계값 (8자, 16자 등)
4. **Exception** → 비즈니스 예외 (중복, 규칙 위반)

---

## TDD 구현 순서 (상세)

### Phase 1: PasswordValidator (단위 테스트) - 순수 Java

**테스트 파일**: `PasswordValidatorTest.java`

**작성 순서 (권장):**

```
1. [Zero]     null 또는 빈 문자열 → BAD_REQUEST
2. [Boundary] 정확히 8자 → 성공
3. [Boundary] 7자 (경계-1) → BAD_REQUEST
4. [Boundary] 정확히 16자 → 성공
5. [Boundary] 17자 (경계+1) → BAD_REQUEST
6. [Exception] 허용되지 않는 문자 (한글) → BAD_REQUEST
7. [Exception] 생년월일 YYYYMMDD 포함 → BAD_REQUEST
8. [Exception] 생년월일 YYMMDD 포함 → BAD_REQUEST
9. [One]      모든 규칙 통과 → 성공
```

| # | 테스트 메서드명 | 입력 예시 | 기대 결과 |
|---|----------------|----------|----------|
| 1 | `validate_WithNull_ThrowsBadRequest` | `null` | BAD_REQUEST |
| 2 | `validate_WithExactly8Chars_Succeeds` | `"Abcd123!"` | 성공 |
| 3 | `validate_With7Chars_ThrowsBadRequest` | `"Abc123!"` | BAD_REQUEST |
| 4 | `validate_WithExactly16Chars_Succeeds` | `"Abcd1234!@#$Efgh"` | 성공 |
| 5 | `validate_With17Chars_ThrowsBadRequest` | `"Abcd1234!@#$Efghi"` | BAD_REQUEST |
| 6 | `validate_WithKorean_ThrowsBadRequest` | `"Abcd123한글"` | BAD_REQUEST |
| 7 | `validate_ContainsBirthYYYYMMDD_ThrowsBadRequest` | `"Pass19900115!"` (생년월일: 1990-01-15) | BAD_REQUEST |
| 8 | `validate_ContainsBirthYYMMDD_ThrowsBadRequest` | `"Pass900115!!"` (생년월일: 1990-01-15) | BAD_REQUEST |
| 9 | `validate_WithValidPassword_Succeeds` | `"ValidPass1!"` | 성공 |

### Phase 2: NameMasker (단위 테스트) - 순수 Java

**테스트 파일**: `NameMaskerTest.java`

**작성 순서:**

```
1. [Zero]     null → null 반환 또는 예외
2. [Zero]     빈 문자열 → 빈 문자열
3. [Boundary] 1글자 → "*"
4. [Boundary] 2글자 → "홍*"
5. [One]      3글자 이상 → "홍길*"
```

| # | 테스트 메서드명 | 입력 | 기대 결과 |
|---|----------------|------|----------|
| 1 | `mask_WithNull_ReturnsNull` | `null` | `null` |
| 2 | `mask_WithEmpty_ReturnsEmpty` | `""` | `""` |
| 3 | `mask_With1Char_ReturnsMasked` | `"홍"` | `"*"` |
| 4 | `mask_With2Chars_ReturnsMasked` | `"홍길"` | `"홍*"` |
| 5 | `mask_With3Chars_ReturnsMasked` | `"홍길동"` | `"홍길*"` |

### Phase 3: LoginIdValidator (단위 테스트) - 순수 Java

**테스트 파일**: `LoginIdValidatorTest.java`

**작성 순서:**

```
1. [Zero]     null → BAD_REQUEST
2. [Zero]     빈 문자열 → BAD_REQUEST
3. [Exception] 특수문자 포함 → BAD_REQUEST
4. [Exception] 한글 포함 → BAD_REQUEST
5. [One]      영문+숫자 → 성공
6. [One]      영문만 → 성공
7. [One]      숫자만 → 성공
```

### Phase 4: MemberModel (단위 테스트)

**테스트 파일**: `MemberModelTest.java`

**작성 순서:**

```
1. [Zero]     loginId null → BAD_REQUEST
2. [Zero]     name null → BAD_REQUEST
3. [One]      정상 생성 → 성공
4. [One]      changePassword 호출 → 비밀번호 변경됨
```

| # | 테스트 메서드명 | 기대 결과 |
|---|----------------|----------|
| 1 | `create_WithNullLoginId_ThrowsBadRequest` | BAD_REQUEST |
| 2 | `create_WithNullName_ThrowsBadRequest` | BAD_REQUEST |
| 3 | `create_WithValidInput_Succeeds` | 성공 |
| 4 | `changePassword_UpdatesPassword` | 비밀번호 변경됨 |

### Phase 5: MemberService (통합 테스트)

**테스트 파일**: `MemberServiceIntegrationTest.java`

**작성 순서:**

```
1. [One]      정상 회원가입 → 성공, 비밀번호 암호화됨
2. [Exception] 중복 loginId → CONFLICT
3. [One]      존재하는 회원 조회 → 회원 반환
4. [Exception] 존재하지 않는 회원 조회 → NOT_FOUND
5. [One]      헤더 인증 성공 → 회원 반환
6. [Exception] 헤더 인증 실패 (비밀번호 불일치) → UNAUTHORIZED
7. [One]      정상 비밀번호 변경 → 성공
8. [Exception] 현재 비밀번호 불일치 → BAD_REQUEST
9. [Exception] 새 비밀번호 규칙 위반 → BAD_REQUEST
```

### Phase 6: API E2E 테스트

**테스트 파일**: `MemberV1ApiE2ETest.java`

**작성 순서:**

```
1. [One]      POST 회원가입 성공 → 200
2. [Exception] POST 중복 loginId → 409
3. [Exception] POST 잘못된 loginId 형식 → 400
4. [One]      GET 내 정보 조회 성공 → 200, 이름 마스킹됨
5. [Exception] GET 인증 실패 → 401
6. [One]      PATCH 비밀번호 변경 성공 → 200
7. [Exception] PATCH 현재 비밀번호 불일치 → 400
8. [Exception] PATCH 비밀번호 규칙 위반 → 400
```

---

## 구현 파일 목록

### 1. 의존성 추가
```
apps/commerce-api/build.gradle.kts  # spring-security-crypto 추가
```

### 2. 설정
```
apps/commerce-api/src/main/java/com/loopers/config/
└── PasswordEncoderConfig.java      # BCryptPasswordEncoder Bean
```

### 3. Domain Layer
```
apps/commerce-api/src/main/java/com/loopers/domain/member/
├── MemberModel.java                # 엔티티 (BaseEntity 확장)
├── MemberRepository.java           # 저장소 인터페이스
├── MemberService.java              # 도메인 서비스
├── PasswordValidator.java          # 비밀번호 검증
├── LoginIdValidator.java           # 로그인ID 검증
└── NameMasker.java                 # 이름 마스킹
```

### 4. Application Layer
```
apps/commerce-api/src/main/java/com/loopers/application/member/
├── MemberFacade.java               # 유스케이스 조율
└── MemberInfo.java                 # DTO (record)
```

### 5. Infrastructure Layer
```
apps/commerce-api/src/main/java/com/loopers/infrastructure/member/
├── MemberJpaRepository.java        # Spring Data JPA
└── MemberRepositoryImpl.java       # Repository 구현체
```

### 6. Interfaces Layer
```
apps/commerce-api/src/main/java/com/loopers/interfaces/api/member/
├── MemberV1ApiSpec.java            # Swagger 명세
├── MemberV1Controller.java         # REST Controller
└── MemberV1Dto.java                # Request/Response DTO
```

### 7. HTTP 테스트
```
http/commerce-api/member-v1.http    # API 테스트용
```

---

## 핵심 구현 사항

### PasswordValidator.java
```java
public class PasswordValidator {
    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 16;
    private static final Pattern ALLOWED_PATTERN = Pattern.compile("^[a-zA-Z0-9!@#$%^&*()_+\\-=\\[\\]{}|;':\",./<>?]+$");

    public static void validate(String password, LocalDate birthDate) {
        if (password == null || password.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 필수입니다.");
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 8~16자여야 합니다.");
        }
        if (!ALLOWED_PATTERN.matcher(password).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호는 영문 대소문자, 숫자, 특수문자만 가능합니다.");
        }
        if (containsBirthDate(password, birthDate)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.");
        }
    }

    public static void validateForChange(String newPassword, LocalDate birthDate,
                                         String currentEncodedPassword, PasswordEncoder encoder) {
        validate(newPassword, birthDate);
        if (encoder.matches(newPassword, currentEncodedPassword)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "현재 비밀번호는 사용할 수 없습니다.");
        }
    }

    private static boolean containsBirthDate(String password, LocalDate birthDate) {
        if (birthDate == null) return false;
        String yyyymmdd = birthDate.format(DateTimeFormatter.BASIC_ISO_DATE); // 19900115
        String yymmdd = yyyymmdd.substring(2); // 900115
        return password.contains(yyyymmdd) || password.contains(yymmdd);
    }
}
```

### NameMasker.java
```java
public class NameMasker {
    private static final char MASK_CHAR = '*';

    public static String mask(String name) {
        if (name == null) return null;
        if (name.isEmpty()) return "";
        if (name.length() == 1) return String.valueOf(MASK_CHAR);
        return name.substring(0, name.length() - 1) + MASK_CHAR;
    }
}
```

### LoginIdValidator.java
```java
public class LoginIdValidator {
    private static final Pattern PATTERN = Pattern.compile("^[a-zA-Z0-9]+$");

    public static void validate(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (!PATTERN.matcher(loginId).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 영문과 숫자만 가능합니다.");
        }
    }
}
```

### MemberModel.java
```java
@Entity
@Table(name = "member")
public class MemberModel extends BaseEntity {

    @Column(nullable = false, unique = true)
    private String loginId;

    @Column(nullable = false)
    private String password;  // BCrypt 암호화

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private LocalDate birthDate;

    private String email;

    protected MemberModel() {}

    public MemberModel(String loginId, String password, String name,
                       LocalDate birthDate, String email) {
        if (loginId == null || loginId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "로그인 ID는 필수입니다.");
        }
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이름은 필수입니다.");
        }
        this.loginId = loginId;
        this.password = password;
        this.name = name;
        this.birthDate = birthDate;
        this.email = email;
    }

    public void changePassword(String newEncodedPassword) {
        this.password = newEncodedPassword;
    }

    // getters...
}
```

---

## 참조 파일 (기존 패턴)
- `domain/example/ExampleModel.java` - Entity 패턴
- `domain/example/ExampleService.java` - Service 패턴
- `interfaces/api/ExampleV1ApiE2ETest.java` - E2E 테스트 패턴
- `modules/jpa/.../BaseEntity.java` - BaseEntity 구조

---

## 검증 방법

### 1. 단위 테스트
```bash
./gradlew test --tests "*PasswordValidatorTest"
./gradlew test --tests "*NameMaskerTest"
./gradlew test --tests "*LoginIdValidatorTest"
./gradlew test --tests "*MemberModelTest"
```

### 2. 통합 테스트
```bash
./gradlew test --tests "*MemberServiceIntegrationTest"
```

### 3. E2E 테스트
```bash
./gradlew test --tests "*MemberV1ApiE2ETest"
```

### 4. HTTP 파일로 수동 테스트
```bash
# 인프라 실행
docker-compose -f ./docker/infra-compose.yml up

# 앱 실행 후 http/commerce-api/member-v1.http 실행
```
