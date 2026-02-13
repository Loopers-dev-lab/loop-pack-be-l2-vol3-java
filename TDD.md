# TDD Implementation Guide

> This document defines **Test-Driven Development (TDD)** and **quality assurance principles** that MUST be followed for all feature implementations.

> Production code without tests is **prohibited**. The cycle and principles below must be strictly adhered to.

---

## 1. Development Workflow: TDD 3-Phase Cycle

All test code is written based on the **3A principle (Arrange – Act – Assert)**.

### Step 1. Red Phase (Write a failing test)

- Write the **minimum failing test** that satisfies the requirement first.
- Compilation errors count as failure; define interfaces and specifications first.

### Step 2. Green Phase (Write code to pass the test)

- Write the **simplest code** that makes the Red Phase test pass.
- Avoid over-engineering (e.g. speculative future extensions) at this stage.

### Step 3. Refactor Phase (Improve code quality)

- Improve readability, remove duplication, and optimize while **keeping tests green**.
- Avoid exposing unnecessary `private` methods; re-evaluate object-oriented responsibilities.
- Remove unused imports and debug logs (e.g. `println`).
- Check for missing `final`, inadequate `Optional` handling, etc.

---

## 2. Test Design Principles

### Core design guidelines

- **Isolate external dependencies**: External APIs, DB, and libraries are abstracted behind interfaces and injected via constructor (DI).
- **Cohesive business logic**: Logic is concentrated in domain entities or dedicated domain services.
- **State-centric design**: Maintain the structure `[Input] → [State change] → [Result verification]`.
- **Test levels**: Verify in order **Unit (domain)** → **Integration (application/DB)** → **E2E (API/HTTP)**.

### Test double usage criteria

- **Unit**
  - **Target:** Domain model (Entity, VO, Policy)
  - **Purpose:** Validate pure business logic and rules
  - **Environment:** Plain JVM (no Spring)
  - **Tech:** JUnit 5, AssertJ, Mockito

- **Integration**
  - **Target:** Service, Facade, Repository
  - **Purpose:** Verify component collaboration and business flow
  - **Environment:** `@SpringBootTest`, test DB (H2/Testcontainers)
  - **Tech:** Spring Test Context, Mockito

- **E2E**
  - **Target:** Controller → DB full flow
  - **Purpose:** Validate use-case scenarios via real HTTP requests
  - **Environment:** `MockMvc` or `TestRestTemplate`
  - **Tech:** `@AutoConfigureMockMvc`, RestAssured

---

## 3. TDD-Based Feature Specs & Checklists

Each feature is developed as a single commit unit. The AI must obtain developer approval after each step before proceeding.

- All test methods follow the naming convention: `{methodName}_{testCondition}_{expectedResult}`.
- All tests are structured with **given–when–then** comments.
- Tests must be **independent** and runnable without depending on each other.

### Exception handling strategy

| Layer | Exception type | Notes |
|-------|----------------|--------|
| **Domain (VO, Entity)** | `IllegalArgumentException` | Pure Java |
| **Service** | `CoreException(ErrorType.XXX)` | Business rule violations |
| **API** | Spring maps to HTTP status codes | Automatic |

### Transaction & quality

- All CUD operations must run **inside a transaction** with full rollback on failure.
- Business exceptions must be mapped to appropriate HTTP status codes (400, 401, 404, 409, etc.).
- After each test level (Unit → Integration → E2E), provide a summary of passing tests.

---

## 4. Feature 1: Sign-up

**Goal:** Create a user with validated input and an encrypted password.

### Step 1. Unit tests (Domain layer)

#### UserId VO

- [ ] **Red:** Creating ID longer than 10 characters throws `IllegalArgumentException`.
- [ ] **Red:** Creating UserId with special characters throws exception (UserId allows alphanumeric only).
- [ ] **Red:** Creating UserId with empty string throws `IllegalArgumentException`.
- [ ] **Green:** Creating valid UserId with 10 or fewer characters succeeds (boundary: 10 chars).
- [ ] **Green:** Alphanumeric 4–10 character UserId creates successfully.

#### Email VO

- [ ] **Red:** Missing `@` throws `IllegalArgumentException`.
- [ ] **Red:** Invalid domain format throws `IllegalArgumentException`.
- [ ] **Green:** Standard format `user@example.com` creates successfully.

#### BirthDate VO

- [ ] **Red:** Future date throws `IllegalArgumentException`.
- [ ] **Red:** Wrong format (not `yyyy-MM-dd`) throws `IllegalArgumentException`.
- [ ] **Green:** Past date with correct format creates successfully.

#### Password VO (birth date must not be included per security policy)

- [ ] **Red:** Fewer than 8 characters throws `IllegalArgumentException`.
- [ ] **Red:** More than 16 characters throws `IllegalArgumentException`.
- [ ] **Green:** Valid length 8–16 creates successfully.

#### PasswordPolicy

- [ ] **Red:** Password containing birth date as `yyyyMMdd` violates policy.
- [ ] **Red:** Password containing birth date as `yyMMdd` violates policy.
- [ ] **Red:** Password containing birth date with hyphen `yyyy-MM-dd` violates policy.
- [ ] **Green:** 8–16 chars without birth date creates successfully.

#### Gender enum

- [ ] **Red:** Invalid string format throws `IllegalArgumentException`.
- [ ] **Green:** String `'MALE'` creates successfully.
- [ ] **Green:** String `'FEMALE'` creates successfully.

#### UserModel entity

- [ ] **Green:** `create_withValidInputs_shouldInitializePointsToZero()`.
- [ ] **Green:** `create_shouldStoreExtractedEmailValue()`.

#### Refactor

- [ ] Improve readability of VO validation logic and remove duplication.
- [ ] Extract birth-date pattern logic into `PasswordPolicy` (SRP).

---

### Step 2. Integration tests (Application layer)

- [ ] **Red:** Duplicate ID sign-up attempt throws `ConflictException`.
- [ ] **Red (Concurrency):** Concurrent sign-up with same ID: only one succeeds, others fail with exception.
- [ ] **Green:** On successful sign-up, `UserRepository.save()` is called (verify/spy).
- [ ] **Green:** DB unique constraint violation is translated to `ConflictException` in service layer.
- [ ] **Green:** Password is stored encrypted (no plaintext).
- [ ] **Green:** Stored password is hashed with configured algorithm (e.g. BCrypt) and verifiable via `PasswordEncoder.matches()`.
- [ ] **Refactor:** Check missing `final` and dependency injection structure.

---

### Step 3. E2E tests (API layer)

- [ ] **Red:** Missing gender returns `400 Bad Request`.
- [ ] **Red:** Missing user ID returns `400 Bad Request`.
- [ ] **Green:** Valid sign-up returns `201 Created` and created user info (ID, etc.).
- [ ] **Green:** Request with existing ID returns `409 Conflict`.
- [ ] **Refactor:** DTO validation annotations (`@NotBlank`, `@Pattern`, etc.).
- [ ] **Refactor:** `ApiResponse` structure consistency (meta / data).

---

## 5. Feature 2: My Info

**Goal:** Retrieve user info according to security policy and return it with name masking.

### Step 1. Unit tests (Domain / Policy layer)

#### NameMaskingPolicy

- [ ] **Red:** Masking null name throws exception (name must exist).
- [ ] **Red:** Masking empty string name throws exception (invalid name).
- [ ] **Red:** Masking name that is only whitespace throws exception (invalid after trim).
- [ ] **Red:** Masking single-character name throws exception (below minimum length for policy).
- [ ] **Green:** Two-character name: last character masked (e.g. "김철" → "김*").
- [ ] **Green:** Three or more characters: only last character masked (e.g. "홍길동" → "홍길*").
- [ ] **Green:** English name: last character masked (e.g. "Alan" → "Ala*").
- [ ] **Refactor:** Edge-case handling consistency; remove redundant conditionals.

---

### Step 2. Integration tests (Application layer)

#### UserService

- [ ] **Red:** My-info for non-existent user ID returns null (invalid user handling).
- [ ] **Green:** My-info for existing user ID returns user info (happy path).
- [ ] **Green:** My-info response has name masking policy applied.
- [ ] **Green:** My-info response does not include encrypted password (no sensitive data exposure).
- [ ] **Refactor:** Optional usage and null safety; service method naming consistency.

---

### Step 3. E2E tests (API layer)

- [ ] **Red:** Missing auth header returns `401 Unauthorized`.
- [ ] **Red:** Request with non-existent ID returns `404 Not Found`.
- [ ] **Green:** Valid request returns `200 OK` and correct JSON body.
- [ ] **Refactor:** Response JSON (wrapper) consistency; move auth to global Filter/Interceptor to avoid controller duplication.

---

## 6. Feature 3: Point retrieval

**Goal:** Accurately retrieve the current point balance for a given user.

### Step 1. Integration tests (Application layer)

#### UserService

- [ ] **Red:** Point lookup for non-existent user ID returns null (invalid user).
- [ ] **Red:** User with no point data: failing test for default 0 return (if applicable).
- [ ] **Green:** New user point lookup returns 0 (default point invariant).
- [ ] **Green:** After point accrual, lookup returns correct accumulated points.
- [ ] **Refactor:** Point logic lives in domain model; service layer only handles retrieval.

---

### Step 2. E2E tests (API layer)

- [ ] **Red:** Point request without login ID header returns `400 Bad Request`.
- [ ] **Green:** Authenticated request returns `200 OK` and point info.
- [ ] **Refactor:** Update API docs (e.g. Swagger); header name consistency (`X-Loopers-LoginId`).

---

## 7. Feature 4: Password update

**Goal:** Safely update password after verifying current password and policy.

### Step 1. Unit tests (Domain layer)

#### UserModel

- [ ] **Red:** Changing to same password as current throws exception (no reuse).
- [ ] **Red:** Changing to password shorter than 8 chars throws exception.
- [ ] **Red:** Changing to password longer than 16 chars throws exception.
- [ ] **Green:** Valid new password change updates password correctly in domain.
- [ ] **Refactor:** Encapsulation of `changePassword`; reuse of Password VO.

---

### Step 2. Integration tests (Application layer)

#### UserService

- [ ] **Red:** Wrong current password causes change to fail.
- [ ] **Red:** Current password mismatch causes change to fail (auth failure).
- [ ] **Red:** New password containing birth date causes change to fail (policy).
- [ ] **Green:** Valid input results in password update persisted in DB.
- [ ] **Green:** Password is encrypted before save (no plaintext).
- [ ] **Green:** Same password produces different hash each time (salt).
- [ ] **Refactor:** Minimize DB access (dirty checking); clarify `PasswordEncoder` dependency.

---

### Step 3. E2E tests (API layer)

- [ ] **Red:** Password change without login header returns `401 Unauthorized`.
- [ ] **Red:** Wrong current password returns `400 Bad Request`.
- [ ] **Green:** Valid password change request returns `204 No Content`.
- [ ] **Refactor:** Remove unused imports; final code convention check.
