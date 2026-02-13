# CLAUDE.md

이 파일은 Claude Code가 이 프로젝트를 이해하는 데 필요한 컨텍스트를 제공합니다.

## 프로젝트 개요

Spring + Java 기반 멀티 모듈 이커머스 프로젝트입니다. 상품, 주문, 회원, 결제 등 커머스 도메인을 직접 설계하고 구현하며, API, Batch, Streamer 애플리케이션으로 구성됩니다.

## 기술 스택 및 버전

### Core
- **Java**: 21
- **Spring Boot**: 3.4.4
- **Spring Cloud**: 2024.0.1
- **Gradle**: 8.13

### Data
- **Spring Data JPA** + **QueryDSL** (Jakarta)
- **MySQL** (mysql-connector-j)
- **Spring Data Redis**
- **Spring Kafka**

### Serialization
- **Jackson** (jackson-datatype-jsr310, jackson-module-kotlin)

### Monitoring & Logging
- **Micrometer** + **Prometheus**
- **Micrometer Tracing** (Brave)
- **Logback Slack Appender**: 1.6.1

### Documentation
- **SpringDoc OpenAPI**: 2.7.0

### Testing
- **JUnit 5** (junit-platform-launcher)
- **Mockito**: 5.14.0
- **SpringMockK**: 4.0.2
- **Instancio**: 5.0.2
- **Testcontainers** (MySQL, Redis, Kafka)

### Build Tools
- **Lombok**
- **JaCoCo** (코드 커버리지)

## 모듈 구조

```
Root (loopers-java-spring-template)
├── apps/                          # 실행 가능한 Spring Boot 애플리케이션
│   ├── commerce-api/              # REST API 서버 (Web, Actuator, OpenAPI)
│   ├── commerce-batch/            # Spring Batch 애플리케이션
│   └── commerce-streamer/         # Kafka Consumer 애플리케이션
├── modules/                       # 재사용 가능한 설정 모듈
│   ├── jpa/                       # JPA + QueryDSL 설정
│   ├── redis/                     # Redis 설정
│   └── kafka/                     # Kafka 설정
└── supports/                      # 부가 기능 애드온 모듈
    ├── jackson/                   # Jackson 직렬화 설정
    ├── logging/                   # Prometheus + Slack Appender
    └── monitoring/                # Micrometer + Prometheus
```

### 모듈 의존성 관계
- **commerce-api**: jpa, redis, jackson, logging, monitoring
- **commerce-batch**: jpa, redis, jackson, logging, monitoring
- **commerce-streamer**: jpa, redis, kafka, jackson, logging, monitoring

## 빌드 및 실행

### 로컬 인프라 실행
```bash
docker-compose -f ./docker/infra-compose.yml up
```

### 모니터링 환경 실행
```bash
docker-compose -f ./docker/monitoring-compose.yml up
# Grafana: http://localhost:3000 (admin/admin)
```

### 빌드
```bash
./gradlew build
```

### 테스트
```bash
./gradlew test
```
- 테스트는 `test` 프로파일로 실행됨
- 타임존: `Asia/Seoul`
- Testcontainers로 MySQL, Redis, Kafka 컨테이너 자동 생성

### 특정 앱 실행
```bash
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun
```

## 프로젝트 설정

- **그룹**: `com.loopers`
- **버전**: Git hash 기반 자동 생성
- **패키지 구조**: `com.loopers.*`

## 주요 패턴

### 테스트 패턴
- `testFixtures` 플러그인 사용 (jpa, redis, kafka 모듈)
- Testcontainers 기반 통합 테스트
- E2E 테스트: `*E2ETest.java`
- 통합 테스트: `*IntegrationTest.java`

### 모듈 규칙
- **apps**: BootJar 활성화, 일반 Jar 비활성화
- **modules/supports**: 일반 Jar 활성화, BootJar 비활성화

### 필수 연관 문서
- plan.md 필수 참고

## 개발자 철학 (API 구현 규칙)

아래 규칙은 이 프로젝트에서 API를 구현할 때 반드시 따라야 하는 원칙이다.

### 1. 에러 처리: 검증 실패는 예외가 아닌 Response Return

- 비즈니스 로직의 **검증 단계에서 발생하는 에러**는 `try/catch` 예외로 던지지 않고, **직접 Response를 return**한다.
- 예외(`throw`)는 **예상치 못한 시스템 에러**에만 사용한다.
- 검증 실패 시 클라이언트에게 **왜 실패했는지 명확한 메시지**를 포함한 응답을 반환한다.
- **의미 없는 `try/catch`는 지양한다.** 별도 처리 로직 없이 단순히 감싸기만 하는 `try/catch`는 작성하지 않는다.

```java
// Good: 검증 실패 → Response return
if (user == null) {
    return ApiResponse.error("존재하지 않는 사용자입니다.");
}

// Bad: 검증 실패를 예외로 던짐
if (user == null) {
    throw new CoreException(ErrorType.USER_NOT_FOUND);
}

// Bad: 의미 없는 try/catch — 잡아서 다시 던지기만 하거나 아무 처리도 없음
try {
    userRepository.save(user);
} catch (Exception e) {
    throw e;
}
```

### 2. 로깅 및 에러 메시지 분리

- **시스템 로그**: 예외 발생 시 `e`가 아닌 구체적인 정보(요청 파라미터, 상태값 등)를 로그에 남긴다.
- **클라이언트 응답**: 시스템 내부 에러 메시지를 노출하지 않고, 사용자가 이해할 수 있는 실패 사유 메시지를 반환한다.
- **예외 전파 시 메시지**: 예외를 감싸서 다시 던질 때 `e` 전체를 그대로 던지지 않고, **사용자가 이해할 수 있는 정확한 메시지**를 직접 작성하여 전달한다. 내부 스택트레이스나 시스템 정보가 그대로 노출되지 않도록 한다.

```java
// Good: 구체적인 로그 + 사용자 친화적 응답
log.error("사용자 조회 실패 - userId: {}, reason: {}", userId, e.getMessage(), e);
return ApiResponse.error("사용자 정보를 불러올 수 없습니다. 잠시 후 다시 시도해주세요.");

// Good: 예외 감싸서 던질 때 — 정확한 메시지 + cause 보존
catch (DateTimeParseException e) {
    throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
            "생년월일은 YYYY-MM-DD 형식이어야 합니다.", e);
    // 클라이언트에는 "YYYY-MM-DD 형식이어야 합니다" 전달
    // 시스템 로그에는 cause(DateTimeParseException)로 원인 추적 가능
}

// Bad: 모호한 로그 + 시스템 에러 노출
log.error("error", e);
return ApiResponse.error(e.getMessage());

// Bad: e 전체를 그대로 메시지로 노출
catch (DateTimeParseException e) {
    throw new CoreException(UserErrorType.INVALID_BIRTH_DATE, e.toString());
    // "java.time.format.DateTimeParseException: Text '1994/11/15'..." 가 클라이언트에 노출됨
}
```

### 3. 주석 규칙

- **정책/도메인 규칙**: 다른 개발자가 반드시 알아야 하는 비즈니스 정책은 상세한 주석을 작성한다.
- **일반 코드**: 간결한 주석만 작성한다. 코드 자체로 의도가 명확하면 주석을 생략한다.

### 4. 테스트 코드 규칙

- 테스트 메서드명은 **한글**로 작성한다.
- 클래스에 `@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)`를 선언하여 언더스코어(`_`)가 공백으로 치환되도록 한다.

```java
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UserServiceTest {

    @Test
    void 존재하지_않는_사용자_조회_시_에러_응답을_반환한다() { ... }

    @Test
    void 비밀번호가_정책에_부합하지_않으면_에러_응답을_반환한다() { ... }
}
```

### 5. 함수명 및 URI 네이밍 규칙

- Controller, Service의 함수명은 **REST/도메인 관점**(`get`, `create`, `update`, `delete`)으로 작성한다.
- Repository는 Spring Data JPA 컨벤션(`find`, `save`, `delete`)을 따른다.
- 함수명에서 **단수와 복수를 혼합하지 않는다**. 일관되게 단수 또는 복수를 사용한다.
- URI와 함수명은 HTTP 메서드와 리소스 단/복수에 맞춰 아래 패턴을 따른다.

| HTTP Method | URI 예시 | Controller/Service 함수명 | Repository 함수명 |
|-------------|----------|--------------------------|-------------------|
| GET | `/users` | `getUsers` | `findAll` |
| GET | `/users/{user_no}` | `getUser` | `findByUserNo` |
| POST | `/users` | `createUser` | `save` |
| PUT | `/users/{user_no}` | `updateUser` | `save` |
| DELETE | `/users/{user_no}` | `deleteUser` | `save` (소프트 삭제) |

- **Path Variable 사용 기준**: 노출되어도 무방한 식별자(예: `user_no`)만 Path에 포함한다. 민감 정보(이메일, 주민번호 등)는 Path에 노출하지 않는다.

### 6. 에러 코드 사용 기준

- **검증 단계 실패**: 에러 코드 없이, 직접 Response return으로 실패 사유를 명시한다.
- **예상치 못한 예외**: 에러 코드를 사용하여 시스템 로그에 추적 가능하도록 한다.

### 7. 모든 CRUD 작업에 검증 단계 필수

- **조회, 등록, 수정, 삭제** 모든 작업에서 데이터 검증 단계를 반드시 거친다.
- 클라이언트로부터 과도한 정보를 받아 그대로 처리하지 않는다. **검증된 데이터만으로** 로직을 수행한다.
- 잘못된 데이터가 조회되거나 존재하지 않는 데이터에 대한 요청이 들어오면, 즉시 실패 Response를 return한다.

```java
// Good: 검증 후 처리
public ApiResponse<?> updateUserInfo(Long userNo, UpdateUserRequest request) {
    User user = userRepository.findById(userNo).orElse(null);
    if (user == null) {
        return ApiResponse.error("존재하지 않는 사용자입니다.");
    }
    if (user.isDeleted()) {
        return ApiResponse.error("이미 탈퇴한 사용자입니다.");
    }
    // 검증 통과 후 업데이트 수행
    user.updateInfo(request.getNickname());
    return ApiResponse.success(user);
}

// Bad: 검증 없이 바로 처리
public ApiResponse<?> updateUserInfo(Long userNo, UpdateUserRequest request) {
    userRepository.updateByUserNo(userNo, request.getNickname());
    return ApiResponse.success();
}
```

### 8. 삭제는 소프트 삭제(Soft Delete)를 지향한다

- 데이터를 물리적으로 삭제(`DELETE FROM`)하지 않고, **`deleted_at` 컬럼을 업데이트**하여 논리 삭제를 수행한다.
- 삭제 API의 내부 구현은 실질적으로 **UPDATE** 처리이다.
- 조회 시 `deleted_at IS NULL` 조건으로 삭제되지 않은 데이터만 필터링한다.

```java
// Good: 소프트 삭제 — deleted_at 업데이트
public ApiResponse<?> deleteUser(Long userNo) {
    User user = userRepository.findById(userNo).orElse(null);
    if (user == null) {
        return ApiResponse.error("존재하지 않는 사용자입니다.");
    }
    if (user.isDeleted()) {
        return ApiResponse.error("이미 탈퇴한 사용자입니다.");
    }
    user.delete(); // deleted_at = LocalDateTime.now()
    return ApiResponse.success();
}

// Bad: 물리 삭제
public ApiResponse<?> deleteUser(Long userNo) {
    userRepository.deleteById(userNo);
    return ApiResponse.success();
}
```

## RESTful API 컨벤션

### 1. URI는 동사가 아닌 명사를 사용한다

- 리소스를 나타내는 URI에는 행위(동사)가 아닌 **명사**를 사용한다.
- 행위는 HTTP Method(GET, POST, PUT, DELETE)로 표현한다.

```
# Good
GET    /users
POST   /users
PUT    /users/{user_no}
DELETE /users/{user_no}

# Bad
GET    /getUsers
POST   /createUser
PUT    /updateUser
DELETE /removeUser
```

### 2. 긴 URI에는 하이픈(`-`)을 사용한다

- URI가 길어질 경우 단어 구분자로 **하이픈(`-`)**을 사용한다.
- 언더스코어(`_`)나 camelCase는 사용하지 않는다.

```
# Good
GET /user-addresses
GET /order-histories

# Bad
GET /user_addresses
GET /orderHistories
```

### 3. 필터링은 기존 GET API에 쿼리 파라미터로 추가한다

- 필터링 조건이 필요할 때 **새로운 API를 만들지 않고**, 기존 GET API에 **URL 쿼리 파라미터**를 붙여서 처리한다.

```
# Good: 기존 API에 쿼리 파라미터로 필터링
GET /users?status=active
GET /users?status=active&role=admin
GET /orders?start-date=2026-01-01&end-date=2026-01-31

# Bad: 필터링 조건별로 새 API 생성
GET /active-users
GET /admin-users
GET /orders-by-date
```

## 코드 품질 컨벤션

### 1. DTO 분리 (Request/Response)

- Controller에서 **Entity를 직접 노출하지 않는다**. 요청/응답 전용 DTO를 사용한다.
- 내부 Entity 구조가 변경되더라도 API 스펙에 영향을 주지 않도록 분리한다.

```java
// Good: 전용 DTO 사용
@PostMapping("/users")
public ApiResponse<?> insertUsers(@RequestBody InsertUserRequest request) {
    ...
    return ApiResponse.success(InsertUserResponse.from(user));
}

// Bad: Entity 직접 노출
@PostMapping("/users")
public ApiResponse<User> insertUsers(@RequestBody User user) {
    ...
    return ApiResponse.success(userRepository.save(user));
}
```

### 2. 계층 간 의존성 규칙

- **`Controller → Service → Repository`** 단방향만 허용한다.
- Service가 Controller를 참조하거나, Repository가 Service를 참조하는 **역방향 의존은 금지**한다.

```
# Good
Controller → Service → Repository

# Bad
Controller ← Service  (역방향)
Repository → Service  (역방향)
Service → Controller  (역방향)
```

### 3. 트랜잭션 관리

- **조회 메서드**에는 `@Transactional(readOnly = true)`를 명시하여 불필요한 쓰기 잠금을 방지한다.
- **변경 작업**(등록, 수정, 삭제)에만 `@Transactional`을 사용한다.

```java
// Good: 조회는 readOnly
@Transactional(readOnly = true)
public ApiResponse<?> selectUsers() { ... }

// Good: 변경은 @Transactional
@Transactional
public ApiResponse<?> updateUserInfo(Long userNo, UpdateUserRequest request) { ... }
```

### 4. 매직 넘버/문자열 금지

- 코드 내 의미가 불명확한 숫자나 문자열 리터럴을 직접 사용하지 않는다.
- **`상수(static final)`** 또는 **`enum`**으로 관리한다.

```java
// Good: 상수로 관리
private static final int MAX_LOGIN_ATTEMPTS = 5;
if (loginAttempts >= MAX_LOGIN_ATTEMPTS) { ... }

// Bad: 매직 넘버
if (loginAttempts >= 5) { ... }
```

### 5. 메서드 단일 책임

- 하나의 메서드는 **하나의 역할만** 수행한다.
- 메서드가 길어지면 의미 단위로 **private 메서드로 분리**한다.

```java
// Good: 역할별 분리
public ApiResponse<?> insertUsers(InsertUserRequest request) {
    ApiResponse<?> validationResult = validateInsertRequest(request);
    if (validationResult != null) {
        return validationResult;
    }
    User user = createUser(request);
    return ApiResponse.success(InsertUserResponse.from(user));
}

private ApiResponse<?> validateInsertRequest(InsertUserRequest request) { ... }
private User createUser(InsertUserRequest request) { ... }
```

### 6. Null 안전성

- `null`을 직접 반환하거나 비교하기보다 **`Optional`을 적극 활용**한다.
- 단, **Entity 필드에는 `Optional`을 사용하지 않고**, 조회 반환 시에만 사용한다.

```java
// Good: Optional 활용
Optional<User> userOpt = userRepository.findByUserNo(userNo);
if (userOpt.isEmpty()) {
    return ApiResponse.error("존재하지 않는 사용자입니다.");
}
User user = userOpt.get();

// Bad: null 직접 비교
User user = userRepository.findByUserNo(userNo);
if (user == null) { ... }
```

### 7. 순환 참조 금지

- Service 간, 또는 계층 간 **순환 참조(Circular Dependency)를 절대 허용하지 않는다**.
- 순환이 발생할 경우 공통 로직을 별도 Service로 분리하거나, 이벤트 기반으로 의존을 끊는다.

```
# Bad: 순환 참조
UserService → OrderService → UserService

# Good: 공통 로직 분리
UserService → UserOrderFacade ← OrderService
```

### 8. Lombok 사용 금지

- **Lombok을 사용하지 않는다.** 생성자, getter 등을 직접 작성한다.
- IDE의 코드 생성 기능이나 `record`를 활용하여 보일러플레이트를 줄인다.

```java
// Good: 직접 작성
public class User {
    private final String name;

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }
}

// Bad: Lombok 사용
@Getter
@AllArgsConstructor
public class User {
    private String name;
}
```

### 9. 의존성 주입은 생성자 + `this` 접두어로 불변성 보장

- 의존성 주입은 **생성자 주입**을 사용하고, 필드는 `private final`로 선언한다.
- 호출 시 반드시 **`this.`** 접두어를 붙여 인스턴스 필드임을 명확히 한다.

```java
// Good: 생성자 주입 + this 사용
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    public UserService(UserRepository userRepository, UserMapper userMapper) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public ApiResponse<?> selectUsers() {
        List<User> users = this.userRepository.findAll();
        return ApiResponse.success(this.userMapper.toResponseList(users));
    }
}

// Bad: @Autowired 필드 주입 + this 미사용
@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public ApiResponse<?> selectUsers() {
        return ApiResponse.success(userRepository.findAll());
    }
}
```

### 10. DTO는 `record`를 사용한다

- Request/Response DTO는 Java `record`로 선언하여 **불변성을 보장**한다.
- `record`는 생성자, getter, `equals`, `hashCode`, `toString`을 자동 제공한다.

```java
// Good: record 사용
public record InsertUserRequest(
    String email,
    String password,
    String nickname
) {}

public record InsertUserResponse(
    Long userNo,
    String email,
    String nickname
) {
    public static InsertUserResponse from(User user) {
        return new InsertUserResponse(user.getUserNo(), user.getEmail(), user.getNickname());
    }
}

// Bad: class + getter
public class InsertUserRequest {
    private String email;
    private String password;
    private String nickname;
    // getter/setter ...
}
```

### 11. Entity 외부 노출 금지

- **Entity를 Controller 응답으로 직접 반환하지 않는다.**
- 반드시 Response DTO로 변환하여 반환한다. 이는 내부 테이블 구조 및 민감 필드 노출을 방지한다.

### 12. Null 체크 검증 로직 필수

- 모든 조회 결과, 외부 입력값, 파라미터에 대해 **null 체크를 필수로 수행**한다.
- null일 경우 즉시 실패 Response를 return한다. null 상태로 로직을 계속 진행하지 않는다.

```java
// Good: null 체크 후 즉시 return
public ApiResponse<?> selectUser(Long userNo) {
    if (userNo == null) {
        return ApiResponse.error("사용자 번호는 필수입니다.");
    }
    Optional<User> userOpt = this.userRepository.findByUserNo(userNo);
    if (userOpt.isEmpty()) {
        return ApiResponse.error("존재하지 않는 사용자입니다.");
    }
    return ApiResponse.success(SelectUserResponse.from(userOpt.get()));
}

// Bad: null 체크 없이 진행
public ApiResponse<?> selectUser(Long userNo) {
    User user = this.userRepository.findByUserNo(userNo).get(); // NoSuchElementException 위험
    return ApiResponse.success(user); // Entity 직접 노출
}
```

## 소프트웨어 설계 원칙

### 1. 기술 도입은 문제 정의가 먼저다

- 새로운 라이브러리나 기술을 도입할 때, **해결하려는 문제를 먼저 명확히 정의**한다.
- 유행이나 편의가 아닌, 성능·복원력·유지보수성을 기준으로 가장 단순하고 강건한 방법을 선택한다.

### 2. 설계의도를 고려한다.

- 서비스 경계를 명확히 하고, 상태 동기화 흐름을 문서화한다.

### 3. 개선과 실험은 반드시 테스트로 검증한다

- 성능 개선, 구조 변경 등은 **테스트 코드로 검증 가능한 상태**에서 진행한다.
- 추측이 아닌, 재현 가능한 테스트와 측정 데이터를 근거로 의사결정한다.

### 4. 설계 의도는 코드와 문서에 함께 남긴다

- 비즈니스 플로우, API 계약, 서비스 간 데이터 흐름 등 **설계 의도를 문서와 주석으로 명시**한다.
- 코드만으로 파악하기 어려운 도메인 맥락은 반드시 기록한다.