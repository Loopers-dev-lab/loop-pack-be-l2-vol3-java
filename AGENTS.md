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

- `**commerce-api`**: Main REST API application
  - Layers: `interfaces` (Controllers) → `application` (Facades) → `domain` (Services, Models, Repositories) → `infrastructure` (JPA Implementations)
  - Dependencies: jpa, redis, jackson, logging, monitoring modules
- `**commerce-batch**`: Spring Batch jobs
  - Job configurations, tasklets, listeners
- `**commerce-streamer**`: Kafka consumer application
  - Stream processing, Kafka listeners

#### `modules/` - Infrastructure Modules (Reusable)

- `**jpa**`: JPA configuration, BaseEntity, QueryDSL setup, Testcontainers for MySQL
- `**redis**`: Redis configuration, Testcontainers for Redis
- `**kafka**`: Kafka configuration, Testcontainers for Kafka

#### `supports/` - Support Modules (Cross-cutting)

- `**jackson**`: Jackson configuration (datetime, serialization)
- `**logging**`: Logback configuration, Slack appender
- `**monitoring**`: Actuator and metrics configuration

### Domain & Object Design Strategy

- **Domain objects** MUST encapsulate business rules. Entities and VOs hold validation, invariants, and domain behavior.
- **Application services (Facades)** assemble different domains and orchestrate domain logic to provide use cases. They MUST NOT contain business rules; delegate to the domain layer.
- If a rule appears in multiple services, it likely belongs in a **domain object** (Entity, VO, or Domain Service). Confirm responsibility and coupling with the team before implementing.
- For each feature, clarify **responsibility and coupling** and align implementation with that intent.

### Code Architecture Pattern & DIP

This project follows **Layered Architecture** and **DIP (Dependency Inversion Principle)**:

- **Dependency direction**: **Presentation → Application → Domain ← Infrastructure**
  - Interfaces (Presentation) depend on Application.
  - Application depends on Domain.
  - **Domain does NOT depend on Infrastructure.** Repository interfaces live in Domain; Infrastructure implements them and thus depends on Domain.

```
interfaces (Controllers, DTOs, Specs)     [Presentation]
    ↓
application (Facades, Info DTOs)          [Application]
    ↓
domain (Services, Models, Repositories)   [Domain - center of dependencies]
    ↑
infrastructure (JPA, Redis, Kafka impl.)  [Infrastructure]
```

- **API request/response DTOs** (interfaces layer) and **Application-layer DTOs (Info)** MUST be kept separate. Do not reuse the same type across layers.
- **Packaging**: Four top-level layer packages, with **domain-based subpackages** under each.
  - Example: `/interfaces/api/{domain}`, `/application/{domain}`, `/domain/{domain}`, `/infrastructure/{domain}`

### Layer Responsibilities & Package Rules


| Layer                         | Responsibility                                                                                                                    | Package rule               |
| ----------------------------- | --------------------------------------------------------------------------------------------------------------------------------- | -------------------------- |
| **Interfaces (Presentation)** | Direct contact with users (Web/Controller). Call Application use cases only. May perform request validation and response mapping. | `/interfaces/api/{domain}` |
| **Application**               | Orchestrate flows and complete use-case functions. Delegate real business logic to the domain as much as possible.                | `/application/{domain}`    |
| **Domain**                    | Core business logic. Must not depend on other layers. All dependency arrows point **toward** the domain.                          | `/domain/{domain}`         |
| **Infrastructure**            | Implement persistence and external tech (JPA, Redis, Kafka). Depends on Domain interfaces; provides what the domain needs.        | `/infrastructure/{domain}` |


Each layer has **clear responsibility and concern**. The domain should be **self-contained** so that it is highly testable.

### DIP (Dependency Inversion Principle)

- Dependency is **inverted**: not **Domain → Infrastructure**, but **Domain (interfaces) ← Infrastructure (implementations)**.
- **Benefits**:
  - Use `FakeOrderRepository` or `InMemoryOrderRepository` for tests without real DB.
  - Structural flexibility (e.g. swap DB or change business logic with minimal impact).
  - Testability (e.g. mock repositories).
- **Repository**: Define the interface in the Domain layer; put the implementation in Infrastructure. Design for testability (e.g. fakes, mocks).
- Write **unit tests** for all core domain logic, including exception and boundary cases.

### Architecture Key Principles

- Domain layer MUST be infrastructure-agnostic (no Spring, JPA annotations in domain logic)
- Facades orchestrate business flows but DO NOT contain business logic
- Services (in Domain) contain business logic and invariants
- Models (Entities) contain domain rules and validations
- Repository **interfaces** are defined in **domain**; **implementations** live in **infrastructure**

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
  - **유비쿼터스 언어**: 도메인 용어는 `.docs/design/00-ubiquitous-language.md`를 기준으로 하며, 코드·API·문서에 동일한 단어를 사용한다.

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
4. **Lombok Usage Guidelines**:
  - **VO/DTO**: Use Java `record` (Lombok not needed)
  - **Entity**: Lombok allowed (`@Getter`, `@NoArgsConstructor(access = PROTECTED)`)
  - **Exception/Enum**: Lombok allowed (`@Getter`, `@RequiredArgsConstructor`)
  - **Service/Facade**: Avoid Lombok in business logic (constructor injection only)
  - **Rationale**: Balance between code brevity and explicit domain logic
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
5. **External Integration & Resilience** (결제, PG, 서드파티 API 연동 시):
  - 외부 호출은 `@Transactional` 메서드 밖에서 수행 (DB 커넥션 점유 방지)
  - Connection/Read Timeout을 명시적으로 설정 (타임아웃 부재 시 스레드·커넥션 고갈)
  - Circuit Breaker로 장애 확산 방지, Fallback으로 내부 시스템 정상 응답 유지
  - 비동기 결제: 콜백 + 결제 상태 조회 API로 복구 가능한 구조 설계
  - 멱등성 보장 (orderId/Idempotency-Key 기반 중복 방지)
  - 설계 검증: `skills/analize_external_integration/SKILL.md` 적용

### 🛡️ Priority Checklist (Every Implementation)

Before committing code, verify:

- **Functionality**: Does it actually work? (Manual/automated testing)
- **Null Safety**: All nullable returns wrapped in `Optional`, inputs validated
- **Thread Safety**: No shared mutable state, consider concurrency implications
- **Testability**: Can this be easily tested? No hidden dependencies?
- **Pattern Consistency**: Does this match existing code patterns?
- **Layer Separation**: No architecture boundary violations?

### 🔒 Protected Areas (Do Not Modify)

The following structures are **locked** and require explicit approval to change:

1. `**modules/jpa/src/main/java/com/loopers/domain/BaseEntity.java`**
  - ID generation strategy, audit fields, lifecycle hooks
2. `**apps/commerce-api/.../interfaces/api/ApiResponse.java**`
  - Response envelope format: `{ meta: { result, errorCode, message }, data }`
3. `**apps/commerce-api/.../support/error/ErrorType.java**`
  - Standard error codes and HTTP status mappings
4. **Authentication Headers**:
  - **대고객** (user_required): `X-Loopers-LoginId`, `X-Loopers-LoginPw` — 로그인 ID/비밀번호로 유저 식별. 인증/인가는 주요 스코프가 아니므로 구현하지 않으며, 유저는 타 유저 정보에 직접 접근할 수 없음.
  - **어드민** (ldap_required): `X-Loopers-Ldap` — LDAP(회사 사내 어드민)으로 식별.
5. **Shared Infrastructure Modules**:
  - `modules/jpa`, `modules/redis`, `modules/kafka`
  - `supports/jackson`, `supports/logging`, `supports/monitoring`

---

## 4. API & Error Specifications

### API Prefix & Authentication


| API Type       | Prefix          | Auth Header(s)                           | Example                                      |
| -------------- | --------------- | ---------------------------------------- | -------------------------------------------- |
| 대고객 (Customer) | `/api/v1`       | `X-Loopers-LoginId`, `X-Loopers-LoginPw` | `POST /api/v1/users`, `GET /api/v1/users/me` |
| 어드민 (Admin)    | `/api-admin/v1` | `X-Loopers-Ldap`                         | `GET /api-admin/v1/orders`                   |


- **대고객**: user_required인 기능은 `X-Loopers-LoginId`(및 필요 시 `X-Loopers-LoginPw`)로 유저 식별. 인증/인가는 주요 스코프가 아니므로 구현하지 않음.
- **어드민**: ldap_required인 기능은 `X-Loopers-Ldap`으로 어드민 식별.
- **CustomerAuthInterceptor**: 로그인이 필요한 고객 API 경로에만 적용. 상품·브랜드 조회 등 비회원 허용 경로는 제외. `.docs/design/02-sequence-diagrams.md` §0, `01-requirements.md` §4.2 참조.
- **AdminAuthInterceptor**: `/api-admin/`** 경로 전 구간 적용.

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
POST http://localhost:8080/api/v1/users
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
- Use `**DatabaseCleanUp`** utility (from `jpa` module testFixtures) to clean DB between tests
- Use `**RedisCleanUp**` utility (from `redis` module testFixtures) to clean Redis between tests

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

### Design & Implementation Plans

- `.docs/design/01-requirements.md`: 요구사항
- `.docs/design/06-payment-implementation-plan.md`: 결제(PG) 연동 및 Resilience 설계
- `.docs/design/08-resilience-workflow-and-decision-perspective.md`: Resilience·워크플로우·의사결정 멘토링 관점(k6, 시퀀스 다이어그램, YAGNI)

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

