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


| Layer                   | Exception type                   | Notes                    |
| ----------------------- | -------------------------------- | ------------------------ |
| **Domain (VO, Entity)** | `IllegalArgumentException`       | Pure Java                |
| **Service**             | `CoreException(ErrorType.XXX)`   | Business rule violations |
| **API**                 | Spring maps to HTTP status codes | Automatic                |


### Transaction & quality

- All CUD operations must run **inside a transaction** with full rollback on failure.
- Business exceptions must be mapped to appropriate HTTP status codes (400, 401, 404, 409, etc.).
- After each test level (Unit → Integration → E2E), provide a summary of passing tests.

