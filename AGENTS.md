# AGENTS.md - AI Agent Development Guidelines

> **⚠️ CRITICAL**: This is the PRIMARY REFERENCE for all AI agents working on this project. All guidelines, constraints, and rules defined here MUST be strictly followed at all times.

---

## 1. Project Context

### Tech Stack & Versions

```
Java: 21
Spring Boot: 3.4.4
Kotlin: 2.0.20 (for build scripts only)
Spring Cloud: 2024.0.1
QueryDSL: Latest (via jakarta)
MySQL: Latest connector
Redis: Latest
Kafka: Latest (Spring Kafka)
```

### Primary Libraries

- **Web**: Spring Boot Starter Web, Actuator
- **ORM**: Spring Data JPA, QueryDSL
- **Serialization**: Jackson (JSR310 support)
- **Documentation**: SpringDoc OpenAPI 2.7.0
- **Testing**: JUnit 5, Mockito 5.14.0, SpringMockK 4.0.2, Instancio JUnit 5.0.2
- **Testcontainers**: MySQL, Redis, Kafka
- **Monitoring**: Actuator + Prometheus + Grafana
- **Logging**: Logback with Slack integration

### Module Structure

This is a **multi-module Gradle project** with three primary categories:

#### `apps/` - Application Modules (Executable)

- **`commerce-api`**: Main REST API application
  - Layers: `interfaces` (Controllers) → `application` (Facades) → `domain` (Services, Models, Repositories) → `infrastructure` (JPA Implementations)
  - Dependencies: jpa, redis, jackson, logging, monitoring modules
- **`commerce-batch`**: Spring Batch jobs
  - Job configurations, tasklets, listeners
- **`commerce-streamer`**: Kafka consumer application
  - Stream processing, Kafka listeners

#### `modules/` - Infrastructure Modules (Reusable)

- **`jpa`**: JPA configuration, BaseEntity, QueryDSL setup, Testcontainers for MySQL
- **`redis`**: Redis configuration, Testcontainers for Redis
- **`kafka`**: Kafka configuration, Testcontainers for Kafka

#### `supports/` - Support Modules (Cross-cutting)

- **`jackson`**: Jackson configuration (datetime, serialization)
- **`logging`**: Logback configuration, Slack appender
- **`monitoring`**: Actuator and metrics configuration

### Code Architecture Pattern

This project follows **Layered Architecture** with strict dependency rules:

```
interfaces (Controllers, DTOs, Specs)
    ↓
application (Facades, Info DTOs)
    ↓
domain (Services, Models, Repositories)
    ↓
infrastructure (JPA Repositories, External APIs)
```

**Key Principles**:

- Domain layer MUST be infrastructure-agnostic (no Spring, JPA annotations in domain logic)
- Facades orchestrate business flows but DO NOT contain business logic
- Services contain all business logic and invariants
- Models (Entities) contain domain rules and validations
- Repository interfaces are defined in domain, implemented in infrastructure

### User Domain Class Design

User domain features (회원 가입, 내 정보 조회, 포인트 조회, 비밀번호 변경) implementation structure.

#### Project Structure (12 Classes)

```
apps/commerce-api/src/main/java/com/loopers/

📦 domain/user/                    [Domain Layer - Business Core]
├── UserModel.java                 # Entity (String fields, domain behavior)
├── UserService.java               # Business logic (duplicate check, queries)
├── UserRepository.java            # Repository interface
├── Password.java                  # VO (validation + encryption, transient)
├── Email.java                     # VO (validation, transient)
├── BirthDate.java                 # VO (validation, transient)
└── Gender.java                    # Enum (value restriction)

📦 application/user/               [Application Layer - Orchestration]
├── UserFacade.java                # Flow coordination (transaction boundary)
└── UserInfo.java                  # Response Info (with name masking)

📦 interfaces/api/user/            [Interface Layer - API Entry Point]
├── UserV1Controller.java          # REST Controller
├── UserV1Dto.java                 # Request/Response DTO (records)
└── UserV1ApiSpec.java             # OpenAPI documentation

📦 infrastructure/user/            [Infrastructure Layer - Persistence]
├── UserJpaRepository.java         # Spring Data JPA
└── UserRepositoryImpl.java        # Repository implementation
```

#### Entity Fields Use Primitive/String Types

```java
@Entity
public class UserModel extends BaseEntity {
    private String email;              // Email VO의 value 추출 저장
    private String birthDate;          // BirthDate VO의 value 추출 저장
    private String encryptedPassword;  // Password.encrypt() 결과 저장
    private Gender gender;             // Enum 직접 저장
    private Long points;
}
```

VOs validate on creation, then values are extracted:

```java
Email emailVO = new Email(emailString);     
this.email = emailVO.value();                

Password password = Password.of(raw, birthDate);  
this.encryptedPassword = password.encrypt();      
```

#### Class Responsibilities Summary

| Class           | Type       | Field Types | Lifecycle  | Responsibility                         |
| --------------- | ---------- | ----------- | ---------- | -------------------------------------- |
| **Email**       | record     | -           | Transient  | Email format validation (xx@yy.zz)     |
| **BirthDate**   | record     | -           | Transient  | Date format validation (yyyy-MM-dd)    |
| **Password**    | class      | String      | Transient  | Password rules + encryption            |
| **Gender**      | enum       | -           | -          | Gender validation and conversion       |
| **UserModel**   | @Entity    | String/Long | Persistent | User state management, domain behavior |
| **UserService** | @Component | -           | Singleton  | Business logic, duplicate check        |
| **UserFacade**  | @Component | -           | Singleton  | Flow orchestration, transactions       |
| **UserInfo**    | record     | -           | Transient  | Domain → DTO conversion, masking       |

---

## 2. Development Rules

### Augmented Coding Workflow

**⚠️ CRITICAL PROCESS**: AI agents operate under human supervision with these **NON-NEGOTIABLE** rules:

1. **Direction & Major Decisions**:

   - You MAY propose architectural changes, major refactoring, or new patterns
   - You MUST wait for explicit approval before implementing them
   - Never assume approval; always ask and wait for confirmation

2. **Interim Reporting Obligation**:

   - Report progress at key milestones (e.g., after test implementation, before production code)
   - If you detect you're repeating actions, implementing unrequested features, or deleting tests, **STOP and report immediately**
   - Developer intervention is required when you deviate from instructions

3. **Design Authority**:
   - The human developer retains final authority on all design decisions
   - Your role is to implement, suggest, and optimize—not to decide unilaterally
   - Respect existing patterns unless explicitly asked to change them

### TDD Implementation (Mandatory)

**🚨 ABSOLUTE REQUIREMENT**:

- **ALL production code MUST be driven by tests**
- **NEVER write production code without tests first**
- Refer to `TDD.md` for comprehensive testing guidelines, patterns, and strategies
- If `TDD.md` doesn't exist yet, create it based on project testing patterns before starting TDD work

**Test Hierarchy**:

1. **Unit Tests** (`@Test`): Domain model validations, business logic in services
2. **Integration Tests** (`@SpringBootTest`): Service layer with real DB (Testcontainers)
3. **E2E Tests** (`@SpringBootTest` + `@AutoConfigureMockMvc`): Full HTTP request/response cycle

**Test Naming Convention**:

```
{메서드명}_{테스트_조건}_{예상_결과}
Example: signUp_withDuplicateId_shouldFail()
```

### Core Principles

1. **Respect Layer Boundaries**:

   - Controllers MUST only delegate to Facades
   - Facades MUST only orchestrate (no if/else policy branches)
   - Services MUST contain all business logic
   - Models MUST enforce domain invariants

2. **Maintain Existing Patterns**:

   - Study existing code before implementing new features
   - Follow established naming conventions, package structures, and patterns
   - Consistency > innovation (unless explicitly asked to innovate)

3. **Document-Driven Changes**:
   - For structural changes (new module, layer, or pattern), update relevant docs FIRST
   - Ensure `.codeguide/`, `README.md`, and this `AGENTS.md` stay synchronized

### Branch & PR Strategy

**Branch Naming Format**: `{type}/{context-detail}`

**Types**:

- `feature/` - New functionality (e.g., `feature/week1-user-signup`)
- `fix/` - Bug fixes (e.g., `fix/week2-point-calculation-bug`)
- `refactor/` - Code refactoring (e.g., `refactor/week3-service-layer`)
- `test/` - Test additions/fixes (e.g., `test/week1-integration-tests`)
- `docs/` - Documentation updates (e.g., `docs/update-api-specs`)

**PR Guidelines**:

- One feature = One branch = One PR
- PRs MUST include corresponding tests (unit + integration + E2E as applicable)
- PRs MUST pass all existing tests
- Follow `.github/pull_request_template.md` structure

---

## 3. Constraints & Recommendations

### ❌ Never Do (Strictly Forbidden)

1. **Non-Functional Code**:

   - Never create stub methods with `TODO` comments
   - Never use unnecessary mocks when real implementations exist
   - Never leave `System.out.println()` or debugging logs

2. **Null Safety Violations**:

   - Java: Use `Optional<T>` for nullable returns, never return null from public methods
   - Validate all inputs; fail fast with meaningful exceptions

3. **Architecture Violations**:

   - ❌ Business logic in Controllers
   - ❌ Policy branches (`if/else` based on business rules) in Facades
   - ❌ Domain models importing Spring/JPA infrastructure (`@Autowired`, etc.)
   - ❌ Direct repository calls from Controllers (must go through Facades)

4. **Lombok Restrictions**:

   - **NEW code and entities**: DO NOT use Lombok
   - Use explicit constructors, getters, and builders
   - Exception: Existing code may retain Lombok temporarily (refactor gradually)

5. **Test Anti-Patterns**:

   - Never use random data in tests (breaks reproducibility)
   - Never delete existing tests without explicit approval
   - Never skip writing tests to "save time"

6. **Forbidden Shortcuts**:
   - Never modify `BaseEntity`, `ApiResponse`, `ErrorType`, or authentication headers without approval
   - Never change shared modules (`modules/`, `supports/`) without discussing impact
   - Never commit secrets (`.env`, `credentials.json`, etc.)

### ✅ Recommendations (Best Practices)

1. **Reusable Object Design**:

   - Prefer composition over inheritance
   - Create small, focused classes with single responsibilities
   - Use records for immutable DTOs (Java 17+)

2. **Performance Optimization**:

   - Suggest N+1 query solutions (QueryDSL fetch joins)
   - Recommend caching strategies (Redis) when appropriate
   - Flag potential bottlenecks in code reviews

3. **API Documentation**:

   - After completing API endpoints, document them in `http/{app-name}/*.http` files
   - Include examples for both success and error cases
   - Use `http-client.env.json` for environment-specific variables

4. **Code Quality**:
   - Write self-documenting code (clear naming > comments)
   - Add Javadoc for public APIs and complex logic
   - Follow Java naming conventions (PascalCase for classes, camelCase for methods/variables)

### 🛡️ Priority Checklist (Every Implementation)

Before committing code, verify:

- [ ] **Functionality**: Does it actually work? (Manual/automated testing)
- [ ] **Null Safety**: All nullable returns wrapped in `Optional`, inputs validated
- [ ] **Thread Safety**: No shared mutable state, consider concurrency implications
- [ ] **Testability**: Can this be easily tested? No hidden dependencies?
- [ ] **Pattern Consistency**: Does this match existing code patterns?
- [ ] **Layer Separation**: No architecture boundary violations?

### 🔒 Protected Areas (Do Not Modify)

The following structures are **locked** and require explicit approval to change:

1. **`modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java`**

   - ID generation strategy, audit fields, lifecycle hooks

2. **`apps/commerce-api/.../interfaces/api/ApiResponse.java`**

   - Response envelope format: `{ meta: { result, errorCode, message }, data }`

3. **`apps/commerce-api/.../support/error/ErrorType.java`**

   - Standard error codes and HTTP status mappings

4. **Authentication Headers**:

   - Customer API: `X-Loopers-LoginId`, `X-Loopers-LoginPw`
   - Admin API: `X-Loopers-Ldap`

5. **Shared Infrastructure Modules**:
   - `modules/jpa`, `modules/redis`, `modules/kafka`
   - `supports/jackson`, `supports/logging`, `supports/monitoring`

---

## 4. API & Error Specifications

### API Prefix & Authentication

| API Type     | Prefix         | Auth Header(s)                           | Example                           |
| ------------ | -------------- | ---------------------------------------- | --------------------------------- |
| Customer API | `/customer/v1` | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | `POST /customer/v1/users/sign-up` |
| Admin API    | `/admin/v1`    | `X-Loopers-Ldap`                         | `GET /admin/v1/orders`            |

### Standard Response Format

**Success Response**:

```json
{
  "meta": {
    "result": "SUCCESS",
    "errorCode": null,
    "message": null
  },
  "data": {
    // response payload
  }
}
```

**Error Response**:

```json
{
  "meta": {
    "result": "FAIL",
    "errorCode": "BAD_REQUEST",
    "message": "Invalid input parameters"
  },
  "data": null
}
```

### Error Type Usage

**Defined in**: `com.loopers.support.error.ErrorType`

| ErrorType        | HTTP Status | Usage                                      |
| ---------------- | ----------- | ------------------------------------------ |
| `BAD_REQUEST`    | 400         | Invalid input, validation failures         |
| `NOT_FOUND`      | 404         | Resource not found                         |
| `CONFLICT`       | 409         | Duplicate resource, business rule conflict |
| `INTERNAL_ERROR` | 500         | Unexpected system errors                   |

**Throwing Exceptions**:

```java
throw new CoreException(ErrorType.BAD_REQUEST, "Email format is invalid");
throw new CoreException(ErrorType.NOT_FOUND, "User not found with id: " + userId);
```

### HTTP File Documentation

After implementing an endpoint, document it in `http/commerce-api/{domain}-v1.http`:

```http
### Sign Up
POST http://localhost:8080/customer/v1/users/sign-up
Content-Type: application/json

{
  "userId": "testuser01",
  "password": "SecurePass123!",
  "email": "test@example.com",
  "birthDate": "1990-01-15",
  "gender": "MALE"
}

### Expected Success Response (201 Created)
### Expected Error Response (400 Bad Request) if userId already exists
```

---

## 5. Feature Implementation Guidelines

Based on `.codeguide/loopers-1-week.md` and project requirements, follow these checklists:

### 🔐 User Sign-Up

**Business Rules**:

- User ID: Alphanumeric, max 10 characters
- Email: Must match `xx@yy.zz` format
- Birth Date: Must match `yyyy-MM-dd` format
- Password: 8-16 characters, MUST NOT contain birth date substring
- Password MUST be encrypted (BCrypt or similar)
- User ID MUST be unique (check before insertion)
- Gender: Required field

**Implementation Checklist**:

- [ ] **Unit Tests**:
  - [ ] User creation fails if userId format is invalid
  - [ ] User creation fails if email format is invalid
  - [ ] User creation fails if birthDate format is invalid
  - [ ] User creation fails if password contains birthDate
- [ ] **Integration Tests**:
  - [ ] Sign-up performs User save operation (verify with spy)
  - [ ] Sign-up fails if userId already exists
- [ ] **E2E Tests**:
  - [ ] Sign-up returns created user info on success
  - [ ] Sign-up returns `400 Bad Request` if gender is missing

**Endpoint**: `POST /customer/v1/users/sign-up`

**Response**:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": "testuser01",
    "email": "test@example.com",
    "birthDate": "1990-01-15",
    "gender": "MALE"
  }
}
```

### 👤 My Info Retrieval

**Business Rules**:

- User ID: Alphanumeric characters only
- Name Masking: Replace last character with `*` (e.g., "홍길동" → "홍길*", "John" → "Joh*")

**Implementation Checklist**:

- [ ] **Integration Tests**:
  - [ ] Returns user info if user exists
  - [ ] Returns null if user does not exist
- [ ] **E2E Tests**:
  - [ ] Returns masked user info on success
  - [ ] Returns `404 Not Found` if user does not exist

**Endpoint**: `GET /customer/v1/users/me`

**Headers**: `X-Loopers-LoginId: {userId}`

**Response**:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": "testuser01",
    "name": "홍길*",
    "email": "test@example.com",
    "birthDate": "1990-01-15",
    "gender": "MALE"
  }
}
```

### 💰 Point Retrieval

**Business Rules**:

- Points are associated with User entity
- Only authenticated users can view their own points

**Implementation Checklist**:

- [ ] **Integration Tests**:
  - [ ] Returns point balance if user exists
  - [ ] Returns null if user does not exist
- [ ] **E2E Tests**:
  - [ ] Returns point balance on success
  - [ ] Returns `400 Bad Request` if `X-USER-ID` header is missing

**Endpoint**: `GET /customer/v1/users/me/points`

**Headers**: `X-USER-ID: {userId}`

**Response**:

```json
{
  "meta": { "result": "SUCCESS", "errorCode": null, "message": null },
  "data": {
    "userId": "testuser01",
    "points": 10000
  }
}
```

### 🔄 Password Update

**Business Rules**:

- New password MUST be different from current password
- New password MUST follow same validation rules as sign-up (8-16 chars, no birth date substring)
- Current password MUST be verified before update

**Implementation Checklist**:

- [ ] Verify current password matches stored encrypted password
- [ ] Validate new password meets requirements
- [ ] Ensure new password differs from current password
- [ ] Encrypt new password before saving

**Endpoint**: `PATCH /customer/v1/users/me/password`

**Request**:

```json
{
  "currentPassword": "OldPass123!",
  "newPassword": "NewSecurePass456!"
}
```

---

## 6. Testing Strategy

### Test Structure

Each feature MUST have three test levels:

1. **Unit Tests** (`src/test/.../domain/{entity}/*Test.java`):

   - Focus: Domain models, value objects, business logic
   - Dependencies: None (pure Java, no Spring context)
   - Example: `UserModelTest`, `PasswordValidatorTest`

2. **Integration Tests** (`src/test/.../domain/{entity}/*IntegrationTest.java`):

   - Focus: Service layer with real database (Testcontainers)
   - Dependencies: `@SpringBootTest`, JPA repositories, database
   - Example: `UserServiceIntegrationTest`

3. **E2E Tests** (`src/test/.../interfaces/api/*E2ETest.java`):
   - Focus: HTTP request/response, full application context
   - Dependencies: `@SpringBootTest`, `@AutoConfigureMockMvc`, MockMvc
   - Example: `UserV1ApiE2ETest`

### Test Data Management

- Use **Instancio** for generating test data (avoid randomness for reproducibility)
- Use **`DatabaseCleanUp`** utility (from `jpa` module testFixtures) to clean DB between tests
- Use **`RedisCleanUp`** utility (from `redis` module testFixtures) to clean Redis between tests

### Test Configuration

- Profile: `spring.profiles.active=test`
- Timezone: `Asia/Seoul` (set in Gradle test task)
- Testcontainers: Auto-configured via `testFixtures` modules

---

## 7. Workflow Summary

### Before Starting Any Task

1. Read this `AGENTS.md` document completely
2. Read `TDD.md` if implementing tests
3. Read `.codeguide/{relevant-guide}.md` for feature-specific requirements
4. Study existing code patterns in the same layer/domain
5. Propose your implementation plan and wait for approval

### During Implementation

1. **Write tests first** (unit → integration → E2E)
2. **Implement production code** to pass tests
3. **Run all tests** (`./gradlew test`)
4. **Document API** in `http/` directory
5. **Report progress** at key milestones

### Before Committing

1. Verify all tests pass
2. Check for architecture violations (use checklist in Section 3)
3. Remove debug logs and print statements
4. Ensure code follows existing patterns
5. Update documentation if necessary

### PR Creation

1. Create feature branch: `{type}/{context-detail}`
2. Ensure all tests pass locally
3. Push branch with descriptive commits
4. Create PR using template (`.github/pull_request_template.md`)
5. Wait for review and approval

---

## 8. Additional Resources

- **Build & Run**: `./gradlew bootRun -p apps/commerce-api`
- **Run Tests**: `./gradlew test`
- **Test Coverage**: `./gradlew jacocoTestReport` (XML report in `build/reports/jacoco/`)
- **API Docs**: `http://localhost:8080/swagger-ui.html` (when running)
- **Actuator**: `http://localhost:8080/actuator` (metrics, health)

### Key Configuration Files

- `gradle.properties`: Versions, project group
- `build.gradle.kts`: Common dependencies, test configuration
- `settings.gradle.kts`: Module definitions
- `apps/commerce-api/src/main/resources/application.yml`: Runtime configuration

### Development Tools

- **Docker Compose**: `docker/infra-compose.yml` (MySQL, Redis, Kafka)
- **Monitoring**: `docker/monitoring-compose.yml` (Prometheus, Grafana)
- **HTTP Client**: Use IntelliJ HTTP Client with files in `http/` directory

---

## 9. Final Reminders

> **This document is your PRIMARY REFERENCE. When in doubt:**
>
> 1. Re-read the relevant section in this document
> 2. Study existing code patterns
> 3. Ask for clarification before implementing
> 4. Never assume or guess—always verify

**Key Mantras**:

- ✅ Tests first, code second
- ✅ Real solutions only, no stubs or TODOs
- ✅ Layer boundaries are sacred
- ✅ Consistency over cleverness
- ✅ Report progress, don't work in silence