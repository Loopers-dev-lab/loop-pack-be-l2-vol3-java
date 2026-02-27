# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**loopers-java-spring-template** — Multi-module Java 21 / Spring Boot 3.4.4 commerce backend (Gradle Kotlin DSL).

Group: `com.loopers` | 감성 이커머스 MVP: 좋아요 → 장바구니 → 주문(결제 대기) 흐름.

## Build & Test Commands

```bash
# Full build (all modules)
./gradlew build

# Run all tests (uses Testcontainers — Docker must be running)
./gradlew test

# Run a single module's tests
./gradlew :apps:commerce-api:test

# Run a single test class
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest"

# Run a single test method
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest.register_WithValidInput_ShouldSuccess"

# Run application
./gradlew :apps:commerce-api:bootRun
./gradlew :apps:commerce-batch:bootRun
./gradlew :apps:commerce-streamer:bootRun

# Local infrastructure (MySQL, Redis, Kafka)
docker compose -f docker/infra-compose.yml up -d

# Monitoring (Prometheus, Grafana)
docker compose -f docker/monitoring-compose.yml up -d
```

Test config: profile=`test`, timezone=`Asia/Seoul`, maxParallelForks=1.

## Module Structure

```
apps/           → Executable Spring Boot applications (BootJar enabled)
  commerce-api/       REST API (Web, Swagger, Actuator)
  commerce-batch/     Spring Batch (주문 만료 배치 등)
  commerce-streamer/  Kafka Consumer
modules/        → Reusable infrastructure configs (java-library, NOT executable)
  jpa/                JPA + QueryDSL + MySQL (provides testFixtures), BaseEntity/BaseStringIdEntity
  redis/              Spring Data Redis (provides testFixtures)
  kafka/              Spring Kafka
supports/       → Add-on modules
  jackson/            Jackson serialization config
  logging/            Logback + Slack appender
  monitoring/         Micrometer + Prometheus
```

Only `apps/*` modules produce BootJar. All other modules produce plain Jar.

## Architecture Pattern (commerce-api)

Layered Architecture with strict dependency direction (DIP):

```
interfaces/ → application/ → domain/ ← infrastructure/
(Controller)   (AppService/Facade)  (Service, Model, Repository interface)   (Repository impl)
```

### Application Layer 적용 기준

| 구분 | 구조 | 해당 도메인 |
|------|------|------------|
| **단순 도메인** | Controller → **AppService** → Service (returns Model) | Example, User, Brand, Like, Stats |
| **복잡한 도메인** | Controller → **Facade** → 여러 Service (returns Model) | Product, Cart, Order |

- **AppService**: 단일 도메인 서비스를 호출하고 Model → Info 변환을 담당하는 얇은 application 레이어 클래스.
- **Facade**: 여러 도메인 서비스를 조합(orchestration)하고 Model → Info 변환을 담당하는 application 레이어 클래스.
- **도메인 서비스는 Model을 반환**한다. Info DTO 변환은 항상 application 레이어(AppService/Facade)에서 수행한다.

### Layer Responsibilities

**interfaces/** — HTTP concerns only. Controllers receive requests, call AppService/Facade, return `ApiResponse<T>`.
- DTOs are inner static classes in a wrapper class (e.g., `UserV1Dto.RegisterRequest`, `UserV1Dto.RegisterResponse`)
- Request DTOs use Bean Validation annotations (`@NotBlank`, `@Size`, etc.)
- Response DTOs have `static from(Info)` factory methods
- All responses wrapped in `ApiResponse<T>` — `record ApiResponse<T>(Metadata meta, T data)`

**application/** — AppService (단순) / Facade (복잡). Orchestrates domain services, sets transaction boundaries, converts Domain Model → Info DTO.
- AppService: `*AppService` (annotated `@Service`) — 단일 서비스 호출 + Model → Info 변환
- Facade: `*Facade` (annotated `@Service`) — 여러 서비스 조합 + Model → Info 변환
- `*Info` DTO는 여기에 위치 — application 레이어에서 정의, interfaces 레이어로 전달
- `@Transactional(readOnly = true)` at class level, `@Transactional` on write methods

**domain/** — Business logic. Service + Model (JPA Entity) + Repository interface.
- `*Service` contains business logic, returns `*Model` (NOT Info DTO)
- `*Model` is the JPA entity with validation logic and factory methods
- `*Repository` is a plain Java interface (no Spring Data extends). Repository 반환 타입도 도메인 레이어 타입만 사용 (application 레이어 DTO 금지)
- 통계 등 집계 쿼리는 `*Projection` DTO를 도메인 레이어에 정의하여 Repository/Service가 반환하고, application 레이어에서 `*Info`로 변환한다 (예: `StatsProjection` → `StatsInfo`)
- Domain models do NOT depend on infrastructure (e.g., password encoding via `PasswordEncoder` interface defined in domain)
- 비즈니스 규칙은 도메인 객체/enum에 캡슐화한다 (예: `UnavailableReason.evaluate()` 정적 팩토리로 주문 불가 사유 판별)
- **도메인 서비스 독립성**: `*Service`는 자신의 Repository만 의존한다. 다른 도메인 서비스를 직접 참조하지 않는다. 크로스 도메인 조합은 반드시 AppService/Facade에서 수행한다.

**infrastructure/** — Implements domain repository interfaces.
- `*RepositoryImpl` delegates to `*JpaRepository` (Spring Data JPA)
- `*JpaRepository` extends `JpaRepository<Model, ID>`

### DTO Conversion Chain

**단순 도메인**: `V1Dto.Request` → AppService → Service (returns Model) → `Info.from(Model)` → `V1Dto.Response`
**복잡한 도메인**: `V1Dto.Request` → Facade → 여러 Service (returns Model) → `Info.from(Model)` → `V1Dto.Response`

## Domain Catalog

| 도메인 | 설명 | Base Entity | Application Layer |
|--------|------|-------------|-------------------|
| **Example** | 예제 도메인 | `BaseEntity` (Long PK) | AppService |
| **User** | 회원 (Like/Cart/Order가 참조) | `BaseStringIdEntity` (String PK) | AppService |
| **Brand** | 브랜드 CRUD, 소프트 삭제, display_status | `BaseStringIdEntity` | AppService |
| **Product** | 상품 CRUD, revision 이력, sale_status | `BaseStringIdEntity` | Facade |
| **ProductStock** | 재고 관리 (on_hand, reserved), CAS hold/release | - | (Product Facade) |
| **Like** | 상품 좋아요 등록/취소 (멱등), 복합 PK | `BaseStringIdEntity` | AppService |
| **Cart** | 장바구니 CRUD, 주문 연계 복원, 복합 PK | `BaseStringIdEntity` | Facade |
| **Order** | 주문 생성(DIRECT/CART), 취소, 만료 | `BaseStringIdEntity` | Facade |
| **Stats** | 운영 통계 (주문 현황, 인기 상품) | - | AppService |

## Entity Base Classes

Two coexisting base entity patterns in `modules/jpa`:

| 항목 | `BaseEntity` (기존) | `BaseStringIdEntity` (신규) |
|------|--------------------|-----------------------------|
| PK | `Long` (auto-increment) | 서브클래스에서 `@Id @UuidGenerator`로 정의 (String UUID 36자) |
| PK 컬럼명 | `id` (고정) | 서브클래스에서 직접 정의 (user_id, brand_id, product_id, order_id) |
| 삭제 방식 | `deletedAt` 단일 | `del_yn` + `deletedAt` 이중 관리 |
| 삭제 메서드 | `delete()` / `restore()` | `softDelete()` / `restore()` (멱등) |
| 적용 대상 | Example | User, Brand, Product, Like, Cart, Order 등 신규 도메인 |

Both provide: `createdAt`, `updatedAt`, `guard()` override for entity validation (`@PrePersist`/`@PreUpdate`).

## API Authentication

| 구분 | Prefix | 인증 방식 |
|------|--------|-----------|
| 고객 API | `/api/v1` | `X-Loopers-LoginId` + `X-Loopers-LoginPw` headers |
| 관리자 API | `/api-admin/v1` | `X-Loopers-Ldap: loopers.admin` header |

## Error Handling

- All business exceptions use `CoreException(ErrorType)` or `CoreException(ErrorType, customMessage)` — NOT `IllegalArgumentException`
- `ErrorType` is an enum with `HttpStatus`, `code` (String), and `message`
- `ApiControllerAdvice` (`@RestControllerAdvice`) catches `CoreException` and returns `ApiResponse.fail(code, message)`
- Error response format: `{"meta": {"result": "FAIL", "errorCode": "...", "message": "..."}, "data": null}`

### ErrorType Categories

| 도메인 | ErrorType codes |
|--------|----------------|
| 범용 | `INTERNAL_ERROR`, `BAD_REQUEST`, `NOT_FOUND`, `CONFLICT`, `VALIDATION_ERROR` |
| User | `USER_NOT_FOUND`, `DUPLICATE_USER_ID`, `INVALID_PASSWORD`, `UNAUTHORIZED`, `PASSWORD_MISMATCH`, `SAME_PASSWORD` |
| Brand | `BRAND_NOT_FOUND`, `DUPLICATE_BRAND` |
| Product | `PRODUCT_NOT_FOUND`, `PRODUCT_NOT_ORDERABLE`, `INVALID_STOCK_UPDATE` |
| Stock | `STOCK_NOT_ENOUGH` |
| Like | `LIKE_PRODUCT_NOT_FOUND` |
| Cart | `CART_ITEM_NOT_FOUND`, `CART_LIMIT_EXCEEDED`, `CART_STOCK_EXCEEDED` |
| Order | `ORDER_NOT_FOUND`, `ORDER_NOT_CANCELLABLE`, `ORDER_NOT_CREATABLE`, `ORDER_ITEM_EMPTY`, `ORDER_PENDING_LIMIT_EXCEEDED` |
| Admin | `ADMIN_UNAUTHORIZED` |

## Key Design Patterns

### CAS 재고 관리 (Compare-And-Set)

조건부 UPDATE로 오버셀(초과 판매) 방지. 다건 주문 시 **productId 오름차순 정렬**로 데드락 방지.

| 연산 | SQL 조건 |
|------|----------|
| **hold** (예약) | `SET reserved += :qty WHERE (on_hand - reserved) >= :qty` |
| **release** (해제) | `SET reserved -= :qty WHERE reserved >= :qty` |

### 주문 상태 머신

`PENDING_PAYMENT` → `CANCELLED` (사용자 취소) / `EXPIRED` (배치 만료). 모든 상태 전이는 CAS로 경쟁 조건 방지.

### 장바구니 복원 멱등성

DIRECT 주문 취소/만료 시 `order_cart_restore` 테이블 `existsById` 확인으로 1회 복원 보장. CART 주문은 장바구니 유지.

### 상품 변경 이력 (ProductRevision)

복합 PK (`product_id` + `revision_seq`). 상품 수정/삭제/복구 시 `before_snapshot`/`after_snapshot` JSON 저장.

## Support Enums (`support/enums/`)

| Enum | 값 | 비고 |
|------|----|------|
| `DisplayStatus` | `ACTIVE`, `HIDDEN` | 브랜드/상품 노출 상태 |
| `ProductSaleStatus` | `ON_SALE`, `TEMP_SOLD_OUT`, `STOPPED` | `isOrderable()` 헬퍼 |
| `OrderStatus` | `PENDING_PAYMENT`, `CANCELLED`, `EXPIRED` | `canCancel()` 헬퍼 |
| `OrderType` | `DIRECT`, `CART` | 주문 유형 |
| `ProductRevisionAction` | `CREATE`, `UPDATE`, `HIDE`, `SALE_STATUS_CHANGE`, `DELETE`, `RESTORE` | 변경 이력 액션 |
| `UnavailableReason` | `DELETED`, `HIDDEN`, `BRAND_DELETED`, `BRAND_HIDDEN`, `STOPPED`, `TEMP_SOLD_OUT`, `OUT_OF_STOCK`, `INVALID_QUANTITY` | 장바구니 항목 주문 불가 사유 |
| `RestoreReason` | `USER_CANCELLED`, `EXPIRED`, `PAYMENT_FAILED`, `PG_CANCELLED` | 복원 사유 |
| `RestoreTriggerSource` | `CANCEL_API`, `PG_WEBHOOK`, `EXPIRE_JOB`, `MANUAL` | 복원 트리거 출처 |

## Key Conventions

- **Lombok**: `@Getter`, `@RequiredArgsConstructor`, `@NoArgsConstructor(access = AccessLevel.PROTECTED)`, `@Builder`
- **Entity creation**: Private constructor + static factory method (e.g., `UserModel.createWithEncodedPassword(...)`)
- **Soft delete**: `softDelete()`/`restore()` on `BaseStringIdEntity` (멱등). 브랜드 삭제 시 소속 상품 연쇄 소프트 삭제.
- **고객 조회 조건**: `del_yn='N'` AND `display_status='ACTIVE'`
- **N+1 방지**: 목록 조회 시 배치 메서드 사용 (예: `findAllByOrderIds()`, `findAllByIds()`). Facade에서 배치 조회 후 Map으로 조합.
- **멱등 처리**: 예외 catch 대신 명시적 존재 확인 (`existsById`) 선호. try-catch `DataIntegrityViolationException` 패턴 지양.
- **EditorConfig**: max line length 130 (tests: off), insert final newline

## Test Patterns

- **Unit tests** (`*Test`): `@ExtendWith(MockitoExtension.class)` with `@Mock`/`@InjectMocks`. AssertJ assertions.
- **Integration tests** (`*IntegrationTest`): `@SpringBootTest` with Testcontainers.
- **E2E tests** (`*E2ETest`): `@SpringBootTest` + `@AutoConfigureMockMvc` + `@Transactional` (auto-rollback). MockMvc.
- **Repository tests**: `@DataJpaTest` with testFixtures from `modules/jpa`.
- **Concurrency tests**: `ExecutorService` + `CountDownLatch` for CAS verification.
- **Testcontainers**: `MySqlTestContainersConfig` (modules/jpa testFixtures), `RedisTestContainersConfig` (modules/redis testFixtures).
- Test naming: `methodName_WithCondition_ShouldExpectedResult`
- Korean `@DisplayName` annotations on all test classes and methods

## Local Infrastructure

- MySQL: `localhost:3306` (user: application / pw: application / db: loopers)
- Redis Master: `localhost:6379`, Replica: `localhost:6380`
- Kafka: `localhost:19092`, UI: `http://localhost:9099`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Grafana: `http://localhost:3000` (admin/admin)

## Design Documents

상세 설계 문서는 `docs/design/` 디렉토리 참조:
- `01-requirements.md` — 요구사항 명세서
- `03-class-diagram.md` — 클래스 다이어그램
- `04-erd.md` — ERD (복합 PK, 인덱스 전략)
- `05-architecture.md` — 종합 아키텍처 설계서
- `07-facade-analysis.md` — Facade 필요성 분석

## 설계 원칙

- 도메인 객체는 비즈니스 규칙을 캡슐화한다. 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.
- 애플리케이션 서비스(AppService/Facade)는 서로 다른 도메인을 조립하여 기능을 제공한다.
- API request/response DTO와 application 레이어의 Info DTO는 분리한다.
- 도메인 모델(`*Model`)은 interfaces 레이어에 노출하지 않는다. application 레이어에서 `*Info` DTO로 변환하여 반환한다.
- 각 기능에 대한 책임과 결합도에 대해 개발자의 의도를 확인하고 개발을 진행한다.
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징한다.

## Implementation Guide

TDD 기반 Phase별 구현 가이드는 `docs/guide/` 디렉토리 참조:
- `README.md` — 전체 구현 전략 및 Phase 개요
- `phase-0` ~ `phase-9` — 레이어별 수평 구현 상세 가이드
