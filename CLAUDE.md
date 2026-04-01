# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**loopers-java-spring-template** — Multi-module Java 21 / Spring Boot 3.4.4 commerce backend (Gradle Kotlin DSL).

Group: `com.loopers` | 감성 이커머스 MVP: 좋아요 → 장바구니 → 주문 → 결제 흐름. 이벤트 기반 아키텍처(Transactional Outbox + Kafka).

## Build & Test Commands

**중요: WSL 성능 최적화** — 프로젝트가 Windows 마운트(`/mnt/c/`)에 있어 Gradle 빌드/테스트가 느리다.
`./gradlew` 명령(build, test, bootRun 등)은 반드시 WSL 네이티브 경로에 동기화한 후 실행한다.

```bash
# ━━ Step 1: 프로젝트 동기화 (테스트/빌드 전 항상 실행) ━━
rsync -a --delete \
  --exclude='.gradle' --exclude='build' --exclude='.idea' --exclude='*.iml' \
  /mnt/c/Users/kdj10/Git/loop-pack-be-l2-vol3-java/ \
  ~/projects/loop-pack-be-l2-vol3-java/

# ━━ Step 2: WSL 네이티브 경로에서 Gradle 명령 실행 ━━
# Full build (all modules)
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew build

# Run all tests (uses Testcontainers — Docker must be running)
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew test

# Run a single module's tests
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-api:test

# Run a single test class
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest"

# Run a single test method
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-api:test --tests "com.loopers.domain.user.UserServiceTest.register_WithValidInput_ShouldSuccess"

# Run application
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-api:bootRun
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-batch:bootRun
cd ~/projects/loop-pack-be-l2-vol3-java && ./gradlew :apps:commerce-streamer:bootRun

# Local infrastructure (MySQL, Redis, Kafka)
docker compose -f docker/infra-compose.yml up -d

# Monitoring (Prometheus, Grafana)
docker compose -f docker/monitoring-compose.yml up -d
```

**규칙:**
- 코드 편집은 항상 원본 경로(`/mnt/c/Users/kdj10/Git/loop-pack-be-l2-vol3-java/`)에서 수행
- `./gradlew` 실행이 필요할 때마다 rsync → cd → gradlew 순서로 실행
- rsync와 gradlew 명령은 하나의 Bash 호출에서 `&&`로 체이닝

Test config: profile=`test`, timezone=`Asia/Seoul`, maxParallelForks=1.

### 테스트 환경 트러블슈팅 (Docker 29+ / Testcontainers)

| 문제 | 원인 | 해결 |
|------|------|------|
| **Testcontainers `BadRequestException (Status 400: {"ID":"","Containers":0,...})`** | Docker 29+는 최소 API version 1.44 요구. Testcontainers 1.21.0 이하는 docker-java API 1.32로 요청하여 빈 응답 수신 | `build.gradle.kts`에서 Testcontainers **1.21.4+** 강제 적용 (`resolutionStrategy.eachDependency`). TC 1.21.4부터 API 1.44로 먼저 시도함 |
| **`SQLSyntaxErrorException` — `option` 컬럼 DDL 실패** | `option`은 MySQL 8.0 예약어. Hibernate DDL 자동 생성 시 백틱 없이 사용되어 구문 오류 | `@Column(name = "\`option\`")` 으로 백틱 이스케이프 (ProductModel) |
| **E2E 테스트에서 userId 불일치로 404** | `@Transactional` 롤백이 auto_increment를 리셋하지 않음. userId를 `1L`로 하드코딩하면 2번째 테스트부터 불일치 | `UserJpaRepository.findByLoginId()`로 실제 등록된 userId 조회 후 사용 |
| **인증 없이 요청 시 400 vs 401** | `CustomerAuthInterceptor`는 헤더 없으면 통과, `AuthUserArgumentResolver`에서 user가 null이면 `UNAUTHORIZED(401)` 반환 | 인증 필요 API에 인증 없이 요청 시 기대값은 `401` |

**`~/.testcontainers.properties` 권장 설정:**
```properties
docker.client.strategy=org.testcontainers.dockerclient.UnixSocketClientProviderStrategy
testcontainers.reuse.enable=true
```

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
CustomerAuthInterceptor (고객 인증, userId를 request attribute에 저장)
    ↓
interfaces/ → application/ → domain/ ← infrastructure/
(Controller)   (Facade only)    (Service, Model, Repository interface)   (Repository impl)
```

### Application Layer 적용 기준

| 구분 | 구조 | 해당 도메인 |
|------|------|------------|
| **단순 도메인** | Controller → **Service** 직접 호출 | User, Brand, Like, Stats |
| **복잡한 도메인** | Controller → **Facade** → 여러 Service | Product, Cart, Order, Coupon, Payment |

- **Facade**: 여러 도메인 서비스를 조합(orchestration)하고, 복잡한 비즈니스 플로우(재고 hold/release, 보상 트랜잭션 등)를 담당하는 application 레이어 클래스.
- **단순 도메인은 AppService 없이 Controller에서 Service를 직접 호출**한다.
- **인증은 `CustomerAuthInterceptor`에서 처리**하고, 인증된 사용자는 `@AuthUser` 어노테이션으로 Controller에 주입된다.

### Layer Responsibilities

**interfaces/** — HTTP concerns only. Controllers receive requests, call Service/Facade, return `ApiResponse<T>`.
- DTOs are inner static classes in a wrapper class (e.g., `UserV1Dto.RegisterRequest`, `UserV1Dto.RegisterResponse`)
- Request DTOs use Bean Validation annotations (`@NotBlank`, `@Size`, etc.)
- Response DTOs have `static from(Model)` factory methods — 도메인 모델에서 직접 변환
- All responses wrapped in `ApiResponse<T>` — `record ApiResponse<T>(Metadata meta, T data)`
- `CustomerAuthInterceptor`: 고객 API 인증 처리, `@AuthUser`로 인증된 사용자 주입
- `AdminAuthInterceptor`: 관리자 API 인증 처리

**application/** — Facade만 사용 (복잡한 도메인). 여러 도메인 서비스를 조합, 트랜잭션 경계 설정.
- Facade: `*Facade` (annotated `@Service`) — 여러 서비스 조합 + 트랜잭션 관리
- `*Info` DTO는 복잡한 도메인(Order, Cart, Product)에서 조합된 결과를 표현할 때만 사용
- `@Transactional(readOnly = true)` at class level, `@Transactional` on write methods

**domain/** — Business logic. Service + Model (JPA Entity) + Repository interface.
- `*Service` contains business logic, returns `*Model`
- `*Model` is the JPA entity with validation logic and factory methods
- `*Repository` is a plain Java interface (no Spring Data extends)
- 통계 등 집계 쿼리는 `*Projection` DTO를 도메인 레이어에 정의하여 Repository/Service가 반환하고, Controller에서 Response DTO로 변환한다
- Domain models do NOT depend on infrastructure (e.g., password encoding via `PasswordEncoder` interface defined in domain)
- 비즈니스 규칙은 도메인 객체/enum에 캡슐화한다 (예: `UnavailableReason.evaluate()` 정적 팩토리로 주문 불가 사유 판별)
- **도메인 서비스 크로스 참조 허용**: `*Service`는 비즈니스 로직 수행을 위해 다른 `*Service`를 참조할 수 있다. (예: `LikeService` → `ProductService`로 상품 존재 검증)

**infrastructure/** — Implements domain repository interfaces.
- `*RepositoryImpl` delegates to `*JpaRepository` (Spring Data JPA)
- `*JpaRepository` extends `JpaRepository<Model, ID>`

### DTO Conversion Chain

**단순 도메인**: `V1Dto.Request` → Service (returns Model) → `V1Dto.Response.from(Model)`
**복잡한 도메인**: `V1Dto.Request` → Facade → 여러 Service (returns Model) → `Info.from(Model)` → `V1Dto.Response.from(Info)`

## Domain Catalog

| 도메인 | 설명 | Base Entity | Application Layer |
|--------|------|-------------|-------------------|
| **Example** | 예제 도메인 | `BaseEntity` (Long PK) | - |
| **User** | 회원 (Like/Cart/Order가 참조) | `BaseStringIdEntity` (Long PK) | Service 직접 호출 |
| **Brand** | 브랜드 CRUD, 소프트 삭제, display_status | `BaseStringIdEntity` | Service 직접 호출 |
| **Product** | 상품 CRUD, revision 이력, sale_status | `BaseStringIdEntity` | Facade |
| **ProductStock** | 재고 관리 (on_hand, reserved), CAS hold/release | - | (Product Facade) |
| **Like** | 상품 좋아요 등록/취소 (멱등), 복합 PK, ProductService 참조 | `BaseStringIdEntity` | Service 직접 호출 |
| **Cart** | 장바구니 CRUD, 주문 연계 복원, 복합 PK | `BaseStringIdEntity` | Facade |
| **Order** | 주문 생성(DIRECT/CART), 취소, 만료 | `BaseStringIdEntity` | Facade |
| **Coupon** | 쿠폰 발급 (Rush 선착순), 3-상태 머신 (AVAILABLE→RESERVED→USED) | `BaseStringIdEntity` | Facade |
| **Payment** | PG 결제 연동, 분산락, Resilience4j | `BaseStringIdEntity` | Facade |
| **Outbox** | Transactional Outbox 이벤트 발행 | - | Service |
| **Stats** | 운영 통계 (주문 현황, 인기 상품) | - | Service 직접 호출 |

## Entity Base Classes

Two coexisting base entity patterns in `modules/jpa`:

| 항목 | `BaseEntity` (기존) | `BaseStringIdEntity` (신규) |
|------|--------------------|-----------------------------|
| PK | `Long` (auto-increment) | 서브클래스에서 `@Id @GeneratedValue(IDENTITY)`로 정의 (Long, bigint auto_increment) |
| PK 컬럼명 | `id` (고정) | 서브클래스에서 직접 정의 (user_id, brand_id, product_id, order_id) |
| 삭제 방식 | `deletedAt` 단일 | `del_yn` + `deletedAt` 이중 관리 |
| 삭제 메서드 | `delete()` / `restore()` | `softDelete()` / `restore()` (멱등) |
| 적용 대상 | Example | User, Brand, Product, Like, Cart, Order 등 신규 도메인 |

Both provide: `createdAt`, `updatedAt`, `guard()` override for entity validation (`@PrePersist`/`@PreUpdate`).

## API Authentication

| 구분 | Prefix | 인증 방식 | 처리 |
|------|--------|-----------|------|
| 고객 API | `/api/v1` | `X-Loopers-LoginId` + `X-Loopers-LoginPw` headers | `CustomerAuthInterceptor` → `@AuthUser` |
| 관리자 API | `/api-admin/v1` | `X-Loopers-Ldap: loopers.admin` header | `AdminAuthInterceptor` |

### 고객 인증 흐름

1. `CustomerAuthInterceptor`가 인증 헤더 검증 및 `UserService.authenticate()` 호출
2. 인증된 `UserModel`을 request attribute에 저장
3. `AuthUserArgumentResolver`가 `@AuthUser` 어노테이션 파라미터에 `UserModel` 주입
4. Controller에서 `@AuthUser UserModel user`로 인증된 사용자 접근

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

### Transactional Outbox + Kafka 이벤트 파이프라인

비즈니스 트랜잭션과 이벤트 발행의 원자성을 보장하는 Outbox 패턴:

1. **Outbox 저장**: `OutboxEventService`가 비즈니스 TX 내에서 `OutboxEventModel` 저장 (`Propagation.MANDATORY`)
2. **Relay 발행**: `OutboxEventRelay`가 5초 주기 폴링 → Kafka 발행 (PENDING → PUBLISHED, 실패 시 지수 백오프 10s→60s)
3. **Consumer 처리**: `commerce-streamer`가 토픽별 Processor로 이벤트 소비 (멱등성 보장: `EventHandledModel`)
4. **Cleanup**: `OutboxCleanupScheduler`가 7일 경과 PUBLISHED 이벤트 삭제

**Kafka 토픽**:
- `catalog-events` (3 partitions, productId 파티셔닝): 좋아요/조회 이벤트
- `order-events` (3 partitions, orderId 파티셔닝): 주문 생성/취소/만료 이벤트
- `coupon-issue-requests` (1 partition, 순서 보장): 선착순 쿠폰 발급

**ApplicationEvent → Outbox 흐름**: `@TransactionalEventListener(AFTER_COMMIT)`로 도메인 이벤트 핸들링 후 Outbox 저장.

### 결제 분산락 + Resilience4j

**분산락**: `PaymentLock` 인터페이스 → Redis SETNX + Lua owner-verify unlock. 30초 TTL. Redis 장애 시 락 없이 진행 (가용성 우선).

**Resilience4j 데코레이터 체인** (`ResilientPgClient`):
- Bulkhead (40 semaphore) → CircuitBreaker (80% failure-rate, 15s open) → Retry (3x, jitter 0.5-1.5s) → HTTP

**TX 분리 패턴**: Outbox 이벤트 TX-1 저장 → PG 호출 (TX 밖) → 결과 TX-2 저장. 커넥션 풀 점유 방지.

### 쿠폰 Rush 발급

선착순 쿠폰의 4-레이어 멱등 방어:
1. `CouponDeduplicationCache`: Redis SETNX 중복 요청 차단
2. `CouponRemainingCache`: Redis 잔여 수량 사전 차감
3. CAS `issued_count` 업데이트로 오버셀 방지
4. `CouponPendingActionRelay`: 3초 폴링으로 RESERVED → USED 확정 또는 복원

**DIP**: `CouponDeduplicationCache`, `CouponRemainingCache`, `CouponIssueMetrics`, `OutboxRelayMetrics` — 도메인 인터페이스로 추출, infrastructure에서 구현.

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
| `CouponActionStatus` | `AVAILABLE`, `RESERVED`, `USED` | 쿠폰 발급 3-상태 머신 |
| `OutboxEventStatus` | `PENDING`, `PUBLISHED`, `CLEANUP` | Outbox 이벤트 상태 |

## Schedulers (commerce-api)

| 스케줄러 | 주기 | 역할 |
|----------|------|------|
| `OutboxEventRelay` | 5초 | Outbox → Kafka 발행 (지수 백오프) |
| `OutboxCleanupScheduler` | - | 7일 경과 PUBLISHED 이벤트 삭제 |
| `OrderExpiryScheduler` | 60초 | PENDING_PAYMENT → EXPIRED (결제 요청 중인 주문 제외) |
| `PaymentPollingScheduler` | - | PG 결제 상태 폴링 (CB-aware fail-fast) |
| `CouponActionRelay` | 3초 | RESERVED → USED 확정 또는 복원 |
| `CartRestoreRetryScheduler` | 매일 05:30 | 실패한 장바구니 복원 재시도 |

별도 `ThreadPoolTaskScheduler` (poolSize=3)로 `@Scheduled` 태스크 실행.

## commerce-streamer (Kafka Consumer)

Kafka 이벤트를 소비하여 집계/처리하는 별도 애플리케이션 (Web 없음):
- `CatalogEventProcessor`: 좋아요/조회 집계 → `ProductMetricsModel` upsert
- `OrderEventProcessor`: 상품별 주문 수 추적 (멱등)
- `CouponIssueProcessor`: 선착순 쿠폰 발급 4-레이어 멱등 처리
- `EventHandledModel`: event_id PK로 중복 처리 방지, 30일 후 자동 삭제

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
- `07-index-cache-strategy.md` — 인덱스 + Redis 캐시 전략 (TTL, cache-aside, write-invalidate)
- `08-order-payment-flow.md` — 주문-결제 상태 머신 + TX 분리 패턴 + PG 콜백 흐름

## 설계 원칙

- 도메인 객체는 비즈니스 규칙을 캡슐화한다. 규칙이 여러 서비스에 나타나면 도메인 객체에 속할 가능성이 높다.
- **단순 도메인은 Controller에서 Service를 직접 호출**한다. AppService 레이어는 제거되었다.
- **복잡한 도메인(Product, Cart, Order)은 Facade를 통해 여러 서비스를 조합**한다.
- **도메인 서비스는 필요시 다른 도메인 서비스를 참조**할 수 있다. (예: `LikeService` → `ProductService`)
- **인증은 Interceptor에서 처리**하고, `@AuthUser`로 Controller에 주입한다.
- Response DTO는 도메인 모델에서 직접 변환한다 (`V1Dto.Response.from(Model)`).
- 패키징 전략은 4개 레이어 패키지를 두고, 하위에 도메인 별로 패키징한다.

## Implementation Guide

TDD 기반 Phase별 구현 가이드는 `docs/guide/` 디렉토리 참조:
- `README.md` — 전체 구현 전략 및 Phase 개요
- `phase-0` ~ `phase-9` — 레이어별 수평 구현 상세 가이드
