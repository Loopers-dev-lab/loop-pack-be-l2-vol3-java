# 감성 이커머스 MVP - TDD 구현 계획

## Context

감성 이커머스 MVP를 **TDD(Red → Green → Refactor)** 방식으로 구현한다.
결제(Phase2) 제외, 그 외 전체 기능(유저, 브랜드, 상품, 좋아요, 장바구니, 주문, 관리자, 통계, 만료 배치) 구현.

**핵심 결정사항**:
- PK: ERD 대로 **String PK (UUID)** → `BaseStringIdEntity` 신규 추가
- 소프트 삭제: `del_yn` + `deleted_at` 이중 관리, 정합성 보장
- 브랜드 삭제 정책: **정책 A** (즉시 소프트 삭제 - 상품 연쇄)
- 재고: CAS(Compare-And-Set) UPDATE, productId 오름차순 정렬 데드락 방지
- User PK: UUID (`BaseStringIdEntity`) + `loginId` 별도 필드 (unique, 인증용)
- 기존 Member 도메인: **레거시로 유지**, 신규 도메인(Like, Cart, Order 등)은 User 참조
- 재고 Hold 전략: 주문서 생성 시 즉시 Hold (TTL 15분), **Phantom Sold-Out 인지 후 완화**
- Hold 남용 방지: 사용자당 동시 PENDING_PAYMENT 주문 **최대 3건** 제한 (NFR-007)
- 재고 테이블 분리: `product_stocks`를 `products`에서 분리 (**Hot Row 격리**)

> **향후 전략 전환**: 트래픽 증가 시 Lazy Hold(결제 시점 차감) 또는 Hybrid(인기상품만 Hold) 방식으로 전환 가능하도록 StockService 인터페이스 설계. 상세 분석은 [ANALYSIS.md - 6. 재고 Hold 전략 분석](./ANALYSIS.md) 참조.

**기존 패턴 참조 파일**:

| 레이어 | 참조 파일 | 비고 |
|--------|-----------|------|
| Domain Model | `domain/user/UserModel.java` | |
| Domain Info DTO | `domain/user/UserInfo.java` | **domain 패키지에 위치** |
| Domain Service | `domain/user/UserService.java` | **Info를 직접 반환** |
| Repository Interface | `domain/user/UserRepository.java` | |
| Infrastructure Impl | `infrastructure/user/UserRepositoryImpl.java` | |
| Controller | `interfaces/api/user/UserV1Controller.java` | **Service 직접 호출 (Facade 없음)** |
| DTO | `interfaces/api/user/UserV1Dto.java` | |
| Base Entity | `modules/jpa/.../BaseStringIdEntity.java` | |
| Error Handling | `support/error/ErrorType.java` | |

---

## 구현 전략: 레이어별 수평 구현 (Layer-by-Layer)

기존 도메인별 수직 구현(Brand 전체 → Product 전체 → ...)을 **레이어 계층별 수평 구현**으로 변경한다.
각 Phase에서 하나의 레이어를 모든 도메인에 걸쳐 구현하므로, 동일 패턴의 반복 학습 효과와 계층별 일관성을 확보한다.

```
Phase 0: 인프라 기반 (BaseStringIdEntity + Enum + ErrorType)           ✅ 완료
Phase 1: User 도메인 (전 레이어 — 선행 의존)                            ✅ 완료
Phase 2: Domain Model — 모든 엔티티 + 복합 PK + Info DTO + 모델 단위 테스트
Phase 3: Domain Service + Repository Interface — Mock 단위 테스트 (Info 반환 포함)
Phase 4: Infrastructure — JpaRepository + RepositoryImpl + 통합/동시성 테스트
Phase 5: Application — Facade (Product, Cart, Order만) + Facade 단위 테스트
Phase 6: Interfaces (Customer API) — Controller + DTO + E2E 테스트
Phase 7: Interfaces (Admin API) — Controller + DTO + E2E 테스트
Phase 8: Batch — OrderExpiryScheduler
Phase 9: Integration + Refactoring — 전체 흐름 통합 시나리오
```

> **Facade 개선안 적용**: Info DTO를 `domain/` 패키지로 이동하여 단순 도메인(User, Brand, Like, Stats)은 Facade 없이 Controller → Service 직접 호출. 복잡한 도메인(Product, Cart, Order)만 Facade 유지. 상세: [07-facade-analysis.md](./07-facade-analysis.md)

### Phase 요약

| Phase | 레이어 | 설명 | 테스트 수 |
|-------|--------|------|-----------|
| 0 | 인프라 기반 | BaseStringIdEntity + Enum + ErrorType | ✅ 19 |
| 1 | User 전체 | User 도메인 전 레이어 (Facade 없이 Service 직접) | ✅ 48 |
| 2 | Domain Model | 모든 JPA Entity + 복합 PK + **Info DTO** + 모델 단위 테스트 | ~73 |
| 3 | Domain Service | 모든 Service + Repository 인터페이스 + Mock 단위 테스트 (**Info 반환 포함**) | ~91 |
| 4 | Infrastructure | 모든 JpaRepository + RepositoryImpl + 통합/동시성 테스트 | ~44 |
| 5 | Application | **3개 Facade만** (Product, Cart, Order) + Facade 단위 테스트 | ~15 |
| 6 | Customer API | 고객용 Controller + DTO + E2E 테스트 (단순 도메인: Service 직접 호출) | ~42 |
| 7 | Admin API | 관리자 Controller + DTO + E2E 테스트 | ~22 |
| 8 | Batch | OrderExpiryScheduler + 테스트 | ~5 |
| 9 | Integration | 전체 흐름 통합 시나리오 + 리팩토링 | ~9 |
| **합계** | | | **~368** |

---

## Phase 0: 인프라 기반 ✅ 완료

### 0-1. BaseStringIdEntity

#### RED - 테스트 먼저
**파일**: `modules/jpa/src/test/java/com/loopers/domain/BaseStringIdEntityTest.java`
```
- create_ShouldGenerateUuidId
- prePersist_ShouldSetTimestamps
- softDelete_ShouldSetDelYnYAndDeletedAt
- softDelete_WhenAlreadyDeleted_ShouldBeIdempotent
- restore_ShouldSetDelYnNAndClearDeletedAt
- restore_WhenNotDeleted_ShouldBeIdempotent
- isDeleted_ShouldReflectDelYnStatus
- delYnAndDeletedAt_ShouldAlwaysBeConsistent
```

#### GREEN - 구현
**파일**: `modules/jpa/src/main/java/com/loopers/domain/BaseStringIdEntity.java`
- `@MappedSuperclass`, PK 없음 — 서브클래스에서 `@Id @UuidGenerator`로 ERD 컬럼명에 맞는 PK 직접 정의 (user_id, brand_id, product_id, order_id)
- `del_yn` (default "N"), `deleted_at`, `created_at`, `updated_at`
- `@PrePersist`에서 UUID 자동 생성 + 타임스탬프 설정
- `softDelete()`: del_yn="Y" + deletedAt=now() (멱등)
- `restore()`: del_yn="N" + deletedAt=null (멱등)
- `guard()` 훅 (서브클래스 검증용)

### 0-2. Enum 정의

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/support/enums/` 하위
```
DisplayStatusTest:
- values_ShouldContain_ACTIVE_HIDDEN

ProductSaleStatusTest:
- values_ShouldContain_ON_SALE_TEMP_SOLD_OUT_STOPPED
- isOrderable_OnSale_ShouldReturnTrue
- isOrderable_TempSoldOut_ShouldReturnFalse
- isOrderable_Stopped_ShouldReturnFalse

OrderStatusTest:
- values_ShouldContain_PENDING_PAYMENT_CANCELLED_EXPIRED
- canCancel_PendingPayment_ShouldReturnTrue
- canCancel_Cancelled_ShouldReturnFalse
- canCancel_Expired_ShouldReturnFalse

OrderTypeTest:
- values_ShouldContain_DIRECT_CART
```

#### GREEN
**파일들** (`apps/commerce-api/src/main/java/com/loopers/support/enums/`):
- `DisplayStatus`: ACTIVE, HIDDEN
- `ProductSaleStatus`: ON_SALE, TEMP_SOLD_OUT, STOPPED + `isOrderable()`
- `OrderType`: DIRECT, CART
- `OrderStatus`: PENDING_PAYMENT, CANCELLED, EXPIRED + `canCancel()`
- `ProductRevisionAction`: CREATE, UPDATE, HIDE, SALE_STATUS_CHANGE, DELETE, RESTORE
- `RestoreReason`: USER_CANCELLED, EXPIRED, PAYMENT_FAILED, PG_CANCELLED
- `RestoreTriggerSource`: CANCEL_API, PG_WEBHOOK, EXPIRE_JOB, MANUAL
- `UnavailableReason`: DELETED, HIDDEN, BRAND_DELETED, BRAND_HIDDEN, STOPPED, TEMP_SOLD_OUT, OUT_OF_STOCK, INVALID_QUANTITY

### 0-3. ErrorType 확장

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/support/error/ErrorTypeExtensionTest.java`
```
- allNewErrorTypes_ShouldHaveCorrectHttpStatusAndCode
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/support/error/ErrorType.java` (기존 파일 수정)
추가할 에러:
```java
// User
USER_NOT_FOUND(404), DUPLICATE_USER_ID(409)
// Brand
BRAND_NOT_FOUND(404), DUPLICATE_BRAND(409)
// Product
PRODUCT_NOT_FOUND(404), PRODUCT_NOT_ORDERABLE(409), INVALID_STOCK_UPDATE(400)
// Stock
STOCK_NOT_ENOUGH(409)
// Like
LIKE_PRODUCT_NOT_FOUND(404)
// Cart
CART_ITEM_NOT_FOUND(404), CART_LIMIT_EXCEEDED(400), CART_STOCK_EXCEEDED(400)
// Order
ORDER_NOT_FOUND(404), ORDER_NOT_CANCELLABLE(409), ORDER_NOT_CREATABLE(409), ORDER_ITEM_EMPTY(400), ORDER_PENDING_LIMIT_EXCEEDED(409)
// Admin
ADMIN_UNAUTHORIZED(401)
```

---

## Phase 1: User 도메인 ✅ 완료

> 기존 Member 도메인은 레거시로 유지한다. 신규 User는 ERD의 `users` 테이블 기반으로, **BaseStringIdEntity(UUID PK) + loginId(unique)** 구조로 구현한다.
> Like, Cart, Order 등 신규 도메인은 User를 참조한다. PasswordEncoder 인터페이스는 `domain/user/` 패키지에 별도 정의한다.

### 1-1. UserModel 단위 테스트

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/user/UserModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithNullLoginId_ShouldThrowCoreException
create_WithBlankLoginId_ShouldThrowCoreException
create_WithNonAlphanumericLoginId_ShouldThrowCoreException
create_DefaultDelYn_ShouldBeN
create_ShouldExtendBaseStringIdEntity

validatePassword_WithValidPassword_ShouldNotThrow
validatePassword_WithTooShort_ShouldThrow
validatePassword_WithTooLong_ShouldThrow
validatePassword_ContainingBirthday_ShouldThrow
validatePassword_WithInvalidChars_ShouldThrow

getMaskedName_ShouldMaskLastChar
getMaskedName_SingleChar_ShouldReturnAsterisk

updatePassword_WithValidEncodedPassword_ShouldUpdate
updatePassword_WithBlank_ShouldThrow

softDelete_ShouldSetDelYnYAndDeletedAt
softDelete_WhenAlreadyDeleted_ShouldBeIdempotent
restore_ShouldSetDelYnNAndClearDeletedAt
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/user/UserModel.java`
- extends `BaseStringIdEntity`
- `@Table(name = "users")`
- 필드: loginId(unique), password(bcrypt), userName, birthday(String), email, address
- 팩토리: `UserModel.createWithEncodedPassword(loginId, encodedPassword, userName, birthday, email, address)`
- 정적 검증: `validatePassword(rawPassword, birthday)` — 8~16자, 특수문자, 생년월일 불가
- 비즈니스: `getMaskedName()`, `updatePassword(encodedPassword)`
- 소프트 삭제: BaseStringIdEntity 상속 (softDelete/restore)

### 1-2. UserService 단위 테스트

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/user/UserServiceTest.java`
```
@ExtendWith(MockitoExtension.class)
@Mock UserRepository userRepository
@Mock PasswordEncoder passwordEncoder

register_WithValidInput_ShouldReturnUser
register_WithDuplicateLoginId_ShouldThrow_DUPLICATE_USER_ID

findById_Existing_ShouldReturn
findById_NotFound_ShouldThrow_USER_NOT_FOUND
findByLoginId_Existing_ShouldReturn
findByLoginId_NotFound_ShouldThrow_USER_NOT_FOUND

authenticate_WithCorrectPassword_ShouldReturnUser
authenticate_WithWrongPassword_ShouldThrow

changePassword_WithCorrectCurrentPw_ShouldUpdate
changePassword_WithWrongCurrentPw_ShouldThrow
changePassword_SameAsOld_ShouldThrow
```

#### GREEN
**파일들**:
- `domain/user/UserRepository.java` (interface)
- `domain/user/PasswordEncoder.java` (interface — Member의 것과 동일 구조)
- `domain/user/UserService.java`

### 1-3. UserRepository 통합 테스트

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/user/UserRepositoryImplTest.java`
```
save_ShouldPersistWithUuidId
findById_Existing_ShouldReturn
findById_NotExisting_ShouldReturnEmpty
findByLoginId_Existing_ShouldReturn
findByLoginId_NotExisting_ShouldReturnEmpty
existsByLoginId_Existing_ShouldReturnTrue
existsByLoginId_NotExisting_ShouldReturnFalse
```

#### GREEN
**파일들**:
- `infrastructure/user/UserJpaRepository.java`
- `infrastructure/user/UserRepositoryImpl.java`
- `infrastructure/user/BCryptPasswordEncoder.java` (기존 Member 패턴 재활용)

### 1-4. UserInfo DTO (domain 패키지)

> Facade 제거에 따라, UserInfo는 `domain/user/` 패키지에 위치하며 `UserService`가 직접 반환한다.

**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/user/UserInfo.java`
- `static from(UserModel)` 팩토리 (userId, loginId, maskedName, birthday, email, address)

### 1-5. User API E2E 테스트

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/interfaces/api/user/UserV1ApiE2ETest.java`
```
@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @Transactional

POST_register_ShouldReturn200
POST_register_DuplicateLoginId_ShouldReturn409
POST_register_InvalidPassword_ShouldReturn400
POST_register_MissingRequiredFields_ShouldReturn400

GET_me_WithAuth_ShouldReturn200
GET_me_WithoutAuth_ShouldReturn401

PATCH_changePassword_WithCorrectCurrentPw_ShouldReturn200
PATCH_changePassword_WithWrongCurrentPw_ShouldReturn401
```

#### GREEN
**파일들**:
- `interfaces/api/user/UserV1Controller.java` — **`UserService` 직접 호출 (Facade 없음)**
  - `POST /api/v1/users` (회원가입 — Guest)
  - `GET /api/v1/users/me` (내 정보 조회 — User)
  - `PATCH /api/v1/users/me/password` (비밀번호 변경 — User)
- `interfaces/api/user/UserV1Dto.java`

---

## Phase 2: Domain Model (모든 엔티티 + Info DTO + 단위 테스트)

**목표**: 나머지 모든 JPA Entity, 복합 PK 클래스, **Info DTO**를 구현하고 단위 테스트로 검증한다.
외부 의존성 없이 모델의 생성/검증/비즈니스 메서드만 테스트한다.
**Info DTO는 `domain/` 패키지에 위치**하며, `static from(Model)` 팩토리 메서드를 제공한다.

**TDD 사이클**: 테스트 작성(RED) → 엔티티 + Info 구현(GREEN) → 리팩토링

**구현 순서** (도메인 간 참조 관계 고려):

### 2-1. BrandModel (~15 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/brand/BrandModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithNullBrandName_ShouldThrowCoreException
create_WithBlankBrandName_ShouldThrowCoreException
create_DefaultDisplayStatus_ShouldBeACTIVE
create_DefaultDelYn_ShouldBeN

hide_ShouldSetDisplayStatusToHIDDEN
activate_ShouldSetDisplayStatusToACTIVE

softDelete_ShouldSetDelYnYAndDeletedAt
softDelete_ShouldBeIdempotent
restore_ShouldSetDelYnNAndClearDeletedAt

isVisibleForCustomer_WhenActiveAndNotDeleted_ShouldReturnTrue
isVisibleForCustomer_WhenHidden_ShouldReturnFalse
isVisibleForCustomer_WhenDeleted_ShouldReturnFalse

updateInfo_WithValidName_ShouldUpdate
updateInfo_WithBlankName_ShouldThrowCoreException
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/brand/BrandModel.java`
- extends `BaseStringIdEntity`, `@Table(name = "brands")`
- 필드: brandName, description, address, displayStatus(`DisplayStatus`), attachFile
- 팩토리: `BrandModel.create(brandName, description, address)`
- 메서드: `hide()`, `activate()`, `updateInfo(...)`, `isVisibleForCustomer()`

**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/brand/BrandInfo.java`
- `static from(BrandModel)` 팩토리

### 2-2. ProductModel (~17 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/ProductModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithNullProductName_ShouldThrow
create_WithNullBrandId_ShouldThrow
create_WithNegativePrice_ShouldThrow
create_WithZeroPrice_ShouldThrow
create_DefaultDisplayStatus_ShouldBeACTIVE
create_DefaultSaleStatus_ShouldBeON_SALE
create_DefaultRevisionSeq_ShouldBe0

updateInfo_ShouldChangeFieldsAndIncrementRevisionSeq
changeSaleStatus_ToTempSoldOut_ShouldUpdate
changeSaleStatus_ToStopped_ShouldUpdate
changeDisplayStatus_ToHidden_ShouldUpdate

isOrderable_WhenActiveAndOnSaleAndNotDeleted_ShouldReturnTrue
isOrderable_WhenHidden_ShouldReturnFalse
isOrderable_WhenTempSoldOut_ShouldReturnFalse
isOrderable_WhenStopped_ShouldReturnFalse
isOrderable_WhenDeleted_ShouldReturnFalse
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductModel.java`
- extends `BaseStringIdEntity`, `@Table(name = "products")`
- 필드: brandId(String), productName, description, price(BigDecimal), category, color, size, option, imageUrl, attachFile, displayStatus, saleStatus(`ProductSaleStatus`), revisionSeq(int)
- `isOrderable()`: displayStatus==ACTIVE && saleStatus.isOrderable() && !isDeleted()
- `incrementRevisionSeq()`

**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductInfo.java`
- `static from(ProductModel, ProductStockModel)` 팩토리 — availableStock, saleStatus 포함

### 2-3. ProductStockModel (~8 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/ProductStockModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithNegativeOnHand_ShouldThrow
create_InitialReserved_ShouldBeZero

getAvailableQty_ShouldReturnOnHandMinusReserved
canHold_WhenSufficient_ShouldReturnTrue
canHold_WhenInsufficient_ShouldReturnFalse

updateOnHand_WithValidQty_ShouldUpdate
updateOnHand_WhenNewOnHandLessThanReserved_ShouldThrow
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/product/ProductStockModel.java`
- `@Entity @Table(name="product_stocks")`, `@Id productId` (String)
- 필드: onHand, reserved(default 0), createdAt, updatedAt
- BaseStringIdEntity 상속하지 않음 (del_yn 불필요, Product 생명주기에 종속)
- `getAvailableQty()`: onHand - reserved
- `canHold(qty)`: availableQty >= qty

### 2-4. ProductRevisionModel + ProductRevisionId (~2 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/ProductRevisionModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithCreateAction_BeforeSnapshotShouldBeNull
```

#### GREEN
**파일들**:
- `domain/product/ProductRevisionModel.java` — `@IdClass(ProductRevisionId.class)`, 복합 PK(productId + revisionSeq)
- `domain/product/ProductRevisionId.java` — `Serializable`
- 필드: action(`ProductRevisionAction`), changedBy, changeReason, beforeSnapshot(JSON), afterSnapshot(JSON), changedAt

### 2-5. LikeModel + LikeId (~4 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/like/LikeModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithNullUserId_ShouldThrow
create_WithNullProductId_ShouldThrow
create_ShouldSetCreatedAt
```

#### GREEN
**파일들**:
- `domain/like/LikeModel.java` — `@IdClass(LikeId.class)`, 복합 PK(userId + productId)
- `domain/like/LikeId.java`
- `domain/like/LikeInfo.java` — `static from(LikeModel)` 팩토리

### 2-6. CartItemModel + CartItemId (~7 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/cart/CartItemModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_WithZeroQuantity_ShouldThrow
create_WithNegativeQuantity_ShouldThrow
changeQuantity_WithValidQty_ShouldUpdate
changeQuantity_WithZeroQty_ShouldThrow
changeQuantity_WithNegativeQty_ShouldThrow
mergeQuantity_ShouldAddToExisting
```

#### GREEN
**파일들**:
- `domain/cart/CartItemModel.java` — `@IdClass(CartItemId.class)`, 복합 PK(userId + productId)
- `domain/cart/CartItemId.java`
- `domain/cart/CartInfo.java` — `static from(CartItemModel, ProductModel, BrandModel, ProductStockModel)` 팩토리
- 필드: quantity(int), createdAt, updatedAt
- 메서드: `changeQuantity(qty)`, `mergeQuantity(qty)`

### 2-7. OrderModel (~15 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_ShouldSetStatus_PENDING_PAYMENT
create_ShouldSetExpiresAt (15분 후)
create_WithNullUserId_ShouldThrow

cancel_WhenPendingPayment_ShouldSetStatus_CANCELLED
cancel_WhenAlreadyCancelled_ShouldBeIdempotent
cancel_WhenExpired_ShouldThrow

expire_WhenPendingPayment_ShouldSetStatus_EXPIRED
expire_WhenAlreadyExpired_ShouldBeIdempotent
expire_WhenCancelled_ShouldThrow

canCancel_WhenPendingPaymentAndNotExpired_True
canCancel_WhenCancelled_False
canCancel_WhenExpired_False
isExpired_WhenExpiresAtPast_True
isExpired_WhenExpiresAtFuture_False
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderModel.java`
- extends `BaseStringIdEntity`, `@Table(name = "orders")`
- 필드: userId, orderType(`OrderType`), status(`OrderStatus`), totalAmount(BigDecimal), expiresAt, paidAt
- 팩토리: `OrderModel.create(userId, orderType, totalAmount)` — status=PENDING_PAYMENT, expiresAt=now+15min
- 메서드: `cancel()`, `expire()`, `canCancel()`, `isExpired()`

**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderInfo.java`
- `static from(OrderModel, List<OrderItemModel>)` 팩토리 — 주문 항목 스냅샷 포함

### 2-8. OrderItemModel + OrderItemId (~3 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderItemModelTest.java`
```
create_WithValidInputs_ShouldCaptureSnapshot
create_WithZeroQuantity_ShouldThrow
getSubtotal_ShouldReturn_unitPrice_times_quantity
```

#### GREEN
**파일들**:
- `domain/order/OrderItemModel.java` — `@IdClass(OrderItemId.class)`, 복합 PK(orderId + orderItemSeq)
- `domain/order/OrderItemId.java`
- 스냅샷 필드: snapshotProductName, snapshotUnitPrice(BigDecimal), snapshotBrandId, snapshotBrandName, snapshotImageUrl

### 2-9. OrderCartRestoreModel (~2 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderCartRestoreModelTest.java`
```
create_WithValidInputs_ShouldSuccess
create_ShouldSetRestoredAt
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/domain/order/OrderCartRestoreModel.java`
- `@Entity @Table(name = "order_cart_restore")`, `@Id orderId` (String PK - 멱등키)
- 필드: userId, reason(`RestoreReason`), triggerSource(`RestoreTriggerSource`), restoredAt

### Phase 2 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.cart.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.*"
```

**산출물: ~24개 소스 파일 (9 Entity + 5 복합PK + 7 Info DTO + 3 기타), ~73개 테스트**

---

## Phase 3: Domain Service + Repository Interface (Mock 단위 테스트)

**목표**: 모든 도메인 Service와 Repository 인터페이스를 구현한다.
**단순 도메인(Brand, Like, Stats)의 Service는 Info를 직접 반환**한다.
Service 테스트는 Mockito로 Repository를 Mock하여 비즈니스 로직만 검증한다.

**TDD 사이클**: Mock 기반 서비스 테스트(RED) → Repository 인터페이스 정의 + Service 구현(GREEN)

**구현 순서** (서비스 간 의존 관계 고려):

### 3-1. BrandService + BrandRepository (~10 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/brand/BrandServiceTest.java`
```
@ExtendWith(MockitoExtension.class)
@Mock BrandRepository brandRepository

createBrand_WithValidInput_ShouldReturnBrand
findById_Existing_ShouldReturn
findById_NotFound_ShouldThrowBRAND_NOT_FOUND
findVisibleById_WhenHidden_ShouldThrowBRAND_NOT_FOUND
findVisibleById_WhenDeleted_ShouldThrowBRAND_NOT_FOUND
findAllVisibleBrands_ShouldReturnOnlyActiveAndNotDeleted
findAllVisibleBrands_WithKeyword_ShouldFilter
updateBrand_ShouldUpdateAndReturn
deleteBrand_ShouldSoftDeleteBrand
deleteBrand_AlreadyDeleted_ShouldBeIdempotent
```

#### GREEN
**파일들**:
- `domain/brand/BrandRepository.java` (interface)
- `domain/brand/BrandService.java`

### 3-2. StockService + ProductStockRepository (~5 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/StockServiceTest.java`
```
@ExtendWith(MockitoExtension.class)
@Mock ProductStockRepository productStockRepository

hold_WithSufficientStock_ShouldReturnTrue
hold_WithInsufficientStock_ShouldThrowSTOCK_NOT_ENOUGH
release_WithValidQty_ShouldReturnTrue
release_WithExcessiveQty_ShouldThrow
commit_WithValidQty_ShouldReturnTrue (Phase2 대비)
```

#### GREEN
**파일들**:
- `domain/product/ProductStockRepository.java` (interface)
  - CAS 메서드: `reserveStock(productId, qty) → int`, `releaseStock(productId, qty) → int`, `commitStock(productId, qty) → int`
- `domain/product/StockService.java`
  - affectedRows 검사, 0이면 `CoreException(STOCK_NOT_ENOUGH)` throw

### 3-3. ProductService + ProductRepository + ProductRevisionRepository (~19 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/ProductServiceTest.java`
```
@ExtendWith(MockitoExtension.class)
@Mock ProductRepository, ProductRevisionRepository, ProductStockRepository, BrandService

createProduct_WithValidInput_ShouldCreateProductAndStock
createProduct_WithNonExistingBrand_ShouldThrow
createProduct_ShouldCreateRevisionWithCREATEAction

findById_Existing_ShouldReturn
findById_NotFound_ShouldThrow
findOrderableById_WhenNotOrderable_ShouldThrow
findAllForCustomer_ShouldReturnOnlyActiveAndNotDeleted
findAllForCustomer_WithKeyword_ShouldFilter
findAllForCustomer_WithBrandId_ShouldFilter

updateProduct_ShouldUpdateAndCreateRevision
updateProduct_ShouldIncrementRevisionSeq
updateProduct_BrandId_ShouldNotBeChangeable

deleteProduct_ShouldSoftDeleteAndCreateRevision
deleteProduct_AlreadyDeleted_ShouldBeIdempotent

softDeleteByBrandId_ShouldDeleteAllProductsOfBrand
softDeleteByBrandId_WhenNoProducts_ShouldBeNoop
changeSaleStatus_ShouldCreateRevision

findRevisionsByProductId_ShouldReturnList
findRevisionById_Existing_ShouldReturn
```

#### GREEN
**파일들**:
- `domain/product/ProductRepository.java` (interface)
- `domain/product/ProductRevisionRepository.java` (interface)
- `domain/product/ProductService.java`

### 3-4. LikeService + LikeRepository (~7 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/like/LikeServiceTest.java`
```
@Mock LikeRepository, ProductService

addLike_NewLike_ShouldCreate
addLike_AlreadyLiked_ShouldBeIdempotent
addLike_ProductNotFound_ShouldThrow

removeLike_Existing_ShouldDelete
removeLike_NotLiked_ShouldBeIdempotent

getMyLikes_ShouldReturnLikeListForUser
countByProductId_ShouldReturnCount
```

#### GREEN
**파일들**:
- `domain/like/LikeRepository.java` (interface)
- `domain/like/LikeService.java`

### 3-5. CartService + CartItemRepository (~17 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/cart/CartServiceTest.java`
```
@Mock CartItemRepository, ProductService, StockService, BrandService

addItem_NewItem_ShouldCreate
addItem_ExistingItem_ShouldMergeQuantity (중복 시 수량 병합)
addItem_NonOrderableProduct_ShouldThrow
addItem_ExceedAvailableStock_ShouldThrow

changeQuantity_ShouldUpdate
changeQuantity_NonExistingItem_ShouldThrow
changeQuantity_ExceedAvailableStock_ShouldThrow

removeItem_ShouldDelete
removeItem_NonExisting_ShouldBeIdempotent

getCart_ShouldReturnAllItemsWithProductInfo
getCart_DeletedProduct_ShouldReturn_available_false_DELETED
getCart_HiddenProduct_ShouldReturn_available_false_HIDDEN
getCart_StoppedProduct_ShouldReturn_available_false_STOPPED
getCart_OutOfStockProduct_ShouldReturn_available_false_OUT_OF_STOCK
getCart_BrandDeleted_ShouldReturn_available_false_BRAND_DELETED

restoreFromOrder_ShouldCreateCartItems
restoreFromOrder_ExistingItem_ShouldMergeQuantity
```

#### GREEN
**파일들**:
- `domain/cart/CartItemRepository.java` (interface)
- `domain/cart/CartService.java`

UnavailableReason 계산 로직 (서비스 계산값):
```java
if (product.isDeleted()) return DELETED;
if (brand.isDeleted()) return BRAND_DELETED;
if (product.displayStatus == HIDDEN) return HIDDEN;
if (brand.displayStatus == HIDDEN) return BRAND_HIDDEN;
if (product.saleStatus == STOPPED) return STOPPED;
if (product.saleStatus == TEMP_SOLD_OUT) return TEMP_SOLD_OUT;
if (stock.availableQty < cartItem.quantity) return OUT_OF_STOCK;
return null; // available=true
```

### 3-6. OrderService + Order Repositories (~28 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderServiceTest.java`
```
@ExtendWith(MockitoExtension.class)
@Mock OrderRepository, OrderItemRepository, OrderCartRestoreRepository
@Mock ProductService, StockService, CartService

=== 바로 주문 (DIRECT) ===
createDirectOrder_ShouldValidateProduct_ReserveStock_SaveOrderAndSnapshot
createDirectOrder_ProductNotOrderable_ShouldThrow
createDirectOrder_InsufficientStock_ShouldThrow
createDirectOrder_ShouldSetOrderType_DIRECT
createDirectOrder_ShouldCalculateTotalAmount
createDirectOrder_ExceedPendingLimit_ShouldThrow_ORDER_PENDING_LIMIT_EXCEEDED

=== 장바구니 주문 (CART) ===
createCartOrder_ShouldValidateAllProducts_ReserveAllStocks
createCartOrder_EmptySelection_ShouldThrow
createCartOrder_PartialStockFailure_ShouldRollbackAllReservations (부분 성공 금지)
createCartOrder_ShouldNotClearCartItems (PENDING_PAYMENT 상태에서는 장바구니 유지)
createCartOrder_DuplicateProductId_ShouldMergeQuantity
createCartOrder_ShouldSortByProductIdAsc (데드락 방지)
createCartOrder_ExceedPendingLimit_ShouldThrow_ORDER_PENDING_LIMIT_EXCEEDED

=== 주문 취소 ===
cancelOrder_ShouldCASTransition_PendingPayment_To_Cancelled
cancelOrder_ShouldReleaseAllStocks
cancelOrder_WhenNotOwner_ShouldThrow
cancelOrder_WhenAlreadyCancelled_ShouldBeIdempotent
cancelOrder_WhenExpired_ShouldThrow_ORDER_NOT_CANCELLABLE
cancelOrder_DIRECT_ShouldRestoreToCart
cancelOrder_DIRECT_RestoreShouldRecordInOrderCartRestore
cancelOrder_DIRECT_SecondCancel_ShouldNotDuplicateRestore (멱등)
cancelOrder_CART_ShouldNotRemoveCartItems (장바구니 유지)

=== 주문 만료 (배치용) ===
expireOrder_ShouldCASTransition_PendingPayment_To_Expired
expireOrder_ShouldReleaseAllStocks
expireOrder_DIRECT_ShouldRestoreToCart
expireOrder_AlreadyExpiredOrCancelled_ShouldSkip (CAS 실패 → no-op)

=== 조회 ===
findByIdAndUserId_Existing_ShouldReturn
findByIdAndUserId_NotOwner_ShouldThrow
findAllByUserId_ShouldReturnOrders
findExpiredPendingOrders_ShouldReturnExpiredOnly
```

#### GREEN
**파일들**:
- `domain/order/OrderRepository.java` (interface)
  - `casUpdateStatus(orderId, from, to) → int`, `countByUserIdAndStatus(userId, status) → long`
- `domain/order/OrderItemRepository.java` (interface)
- `domain/order/OrderCartRestoreRepository.java` (interface)
- `domain/order/OrderService.java`

핵심 구현 패턴:

**PENDING 주문 제한 검증** (NFR-007):
```java
long pendingCount = orderRepository.countByUserIdAndStatus(userId, PENDING_PAYMENT);
if (pendingCount >= 3) {
    throw new CoreException(ORDER_PENDING_LIMIT_EXCEEDED);
}
```

**장바구니 주문 재고 예약 (부분 성공 금지)**:
```java
items.sort(Comparator.comparing(OrderItemRequest::productId)); // 데드락 방지
List<HeldStock> heldStocks = new ArrayList<>();
try {
    for (var item : items) {
        stockService.hold(item.productId(), item.quantity());
        heldStocks.add(new HeldStock(item.productId(), item.quantity()));
    }
} catch (CoreException e) {
    for (var held : heldStocks) {
        stockService.release(held.productId(), held.quantity());
    }
    throw e;
}
```

**주문 취소 CAS 상태 전이**:
```java
int affected = orderRepository.casUpdateStatus(orderId, PENDING_PAYMENT, CANCELLED);
if (affected == 0) {
    Order order = orderRepository.findById(orderId);
    if (order.getStatus() == CANCELLED) return; // 멱등
    throw new CoreException(ORDER_NOT_CANCELLABLE);
}
```

### 3-7. StatsService + StatsRepository (~5 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/stats/StatsServiceTest.java`
```
@Mock StatsRepository

getOverview_ShouldReturnOrderStatusCounts
getDailyOrderStats_ShouldReturnDailyAggregation
getTopLikedProducts_ShouldReturnTopN
getTopOrderedProducts_ShouldReturnTopN
getLowStockProducts_ShouldReturnBelowThreshold
```

#### GREEN
**파일들**:
- `domain/stats/StatsRepository.java` (interface)
- `domain/stats/StatsInfo.java` — 통계 정보 전달 DTO
- `domain/stats/StatsService.java` — **Info를 직접 반환**

### Phase 3 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.*ServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.LikeServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.cart.CartServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderServiceTest"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.stats.StatsServiceTest"
```

**산출물: ~17개 소스 파일 (7 Service + 10 Repository 인터페이스), ~91개 테스트**

---

## Phase 4: Infrastructure (JpaRepository + RepositoryImpl + 통합 테스트)

**목표**: 모든 JpaRepository와 RepositoryImpl을 구현하고, Testcontainers(MySQL)를 사용한 통합 테스트로 실제 DB 동작을 검증한다. CAS 쿼리와 동시성도 이 단계에서 검증한다.

**TDD 사이클**: 통합 테스트(RED) → JpaRepository + RepositoryImpl 구현(GREEN)

**전제**: Docker 실행 필요 (Testcontainers)

### 4-1. Brand Infrastructure (~5 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/brand/BrandRepositoryImplTest.java`
```
save_ShouldPersistWithUuidId
findById_Existing_ShouldReturn
findById_NotExisting_ShouldReturnEmpty
findAllByDelYnAndDisplayStatus_ShouldFilter
findAllByKeyword_ShouldMatchPartialBrandName
```

#### GREEN
**파일들**:
- `infrastructure/brand/BrandJpaRepository.java`
- `infrastructure/brand/BrandRepositoryImpl.java`

### 4-2. Product Infrastructure (~6 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/product/ProductRepositoryImplTest.java`
```
save_ShouldPersistWithUuidId
findById_Existing_ShouldReturn
findById_NotExisting_ShouldReturnEmpty
findAllForCustomer_ShouldReturnOnlyActiveAndNotDeleted
findAllForCustomer_WithKeyword_ShouldFilter
findAllByBrandId_ShouldReturnMatchingProducts
```

#### GREEN
**파일들**:
- `infrastructure/product/ProductJpaRepository.java`
- `infrastructure/product/ProductRepositoryImpl.java`

### 4-3. ProductStock CAS Infrastructure (~4 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/product/ProductStockRepositoryImplTest.java`
```
reserveStock_CAS_WithSufficientStock_ShouldReturnAffectedRows1
reserveStock_CAS_WithInsufficientStock_ShouldReturnAffectedRows0
releaseStock_CAS_ShouldDecreaseReserved
confirmStock_CAS_ShouldDecreaseBothOnHandAndReserved
```

#### GREEN
**파일들**:
- `infrastructure/product/ProductStockJpaRepository.java`
  - `@Query` CAS UPDATE: `SET reserved = reserved + :qty WHERE productId = :pid AND (onHand - reserved) >= :qty`
  - 유사하게 release, commit
- `infrastructure/product/ProductStockRepositoryImpl.java`

### 4-4. ProductRevision Infrastructure (~2 tests)

#### GREEN
**파일들**:
- `infrastructure/product/ProductRevisionJpaRepository.java`
- `infrastructure/product/ProductRevisionRepositoryImpl.java`

### 4-5. 재고 동시성 테스트 (~2 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/product/StockConcurrencyTest.java`
```
@SpringBootTest @ActiveProfiles("test")

concurrentHold_ShouldNotOversell
  → stock=10, 20 스레드 동시 hold(1) → 정확히 10개 성공, availableQty=0
concurrentHoldAndRelease_ShouldMaintainConsistency
```
- `ExecutorService` + `CountDownLatch` 사용

### 4-6. Like Infrastructure (~6 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/like/LikeRepositoryImplTest.java`

#### GREEN
**파일들**:
- `infrastructure/like/LikeJpaRepository.java`
- `infrastructure/like/LikeRepositoryImpl.java`

### 4-7. Cart Infrastructure (~6 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/cart/CartItemRepositoryImplTest.java`

#### GREEN
**파일들**:
- `infrastructure/cart/CartItemJpaRepository.java`
- `infrastructure/cart/CartItemRepositoryImpl.java`

### 4-8. Order Infrastructure + CAS 상태 전환 (~6 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/order/OrderRepositoryImplTest.java`
```
save_ShouldPersistWithUuidId
findById_Existing_ShouldReturn
findByIdAndUserId_ShouldReturn
casUpdateStatus_PendingToCancelled_ShouldReturnAffectedRows1
casUpdateStatus_AlreadyCancelled_ShouldReturnAffectedRows0
findExpiredPendingOrders_ShouldReturnOnlyExpired
```

#### GREEN
**파일들**:
- `infrastructure/order/OrderJpaRepository.java`
  - `@Modifying @Query` CAS: `casUpdateStatus(orderId, fromStatus, toStatus)`
- `infrastructure/order/OrderRepositoryImpl.java`
- `infrastructure/order/OrderItemJpaRepository.java`
- `infrastructure/order/OrderItemRepositoryImpl.java`
- `infrastructure/order/OrderCartRestoreJpaRepository.java`
- `infrastructure/order/OrderCartRestoreRepositoryImpl.java`

### 4-9. Stats Infrastructure (QueryDSL) (~5 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/infrastructure/stats/StatsRepositoryImplTest.java`
```
getOverview_ShouldCountByOrderStatus
getDailyOrderStats_ShouldGroupByDate
getTopLikedProducts_ShouldJoinAndAggregate
getTopOrderedProducts_ShouldJoinAndAggregate
getLowStockProducts_ShouldFilter_onHand_minus_reserved_below_threshold
```

#### GREEN
**파일**: `infrastructure/stats/StatsRepositoryImpl.java` — `JPAQueryFactory` 집계 쿼리

### Phase 4 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.StockConcurrencyTest"
```

**산출물: ~20개 소스 파일, ~44개 테스트**

---

## Phase 5: Application (Facade — 복잡한 도메인만 + 단위 테스트)

**목표**: **여러 서비스를 조합하는 복잡한 도메인만** Facade를 구현한다.
단순 도메인(User, Brand, Like, Stats, Example)은 Phase 3에서 Service가 Info를 직접 반환하므로 Facade가 불필요하다.
Mockito 기반 단위 테스트.

> **Facade 개선안**: 9개 → 3개로 축소. 상세: [07-facade-analysis.md](./07-facade-analysis.md)

**TDD 사이클**: Facade 단위 테스트(RED) → Facade 구현(GREEN)

### 5-1. ProductFacade (~6 tests)

**파일들**:
- `application/product/ProductFacade.java` — `ProductService` + `StockService` + `BrandService` 조합
- **테스트**: `application/product/ProductFacadeTest.java`

### 5-2. CartFacade (~4 tests)

**파일들**:
- `application/cart/CartFacade.java` — `CartService` + `UserService` + `ProductService` + `StockService` 조합
- **테스트**: `application/cart/CartFacadeTest.java`

### 5-3. OrderFacade (~5 tests)

**파일들**:
- `application/order/OrderFacade.java` — `OrderService` + `UserService` + `ProductService` + `StockService` + `CartService` 조합
- **테스트**: `application/order/OrderFacadeTest.java`

### Phase 5 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.application.*"
```

**산출물: ~3개 소스 파일, ~15개 테스트**

---

## Phase 6: Interfaces — Customer API (Controller + DTO + E2E)

**목표**: 고객용 API (`/api/v1/...`) 컨트롤러와 DTO를 구현한다.
E2E 테스트(@SpringBootTest + MockMvc)로 HTTP 요청~응답 전체 흐름을 검증한다.

**의존성 규칙**:
- 단순 도메인(Brand, Like): Controller → Service 직접 호출
- 복잡한 도메인(Product, Cart, Order): Controller → Facade 호출

### 6-1. BrandV1Controller + BrandV1Dto (~5 E2E tests)

**엔드포인트**:
- `GET /api/v1/brands` — 목록 (키워드 검색)
- `GET /api/v1/brands/{brandId}` — 상세

**테스트**: `interfaces/api/brand/BrandV1ApiE2ETest.java`
```
GET_brands_ShouldReturn200WithList
GET_brands_WithKeyword_ShouldFilterResults
GET_brandById_Existing_ShouldReturn200
GET_brandById_NotExisting_ShouldReturn404
GET_brandById_WhenHidden_ShouldReturn404
```

### 6-2. ProductV1Controller + ProductV1Dto (~6 E2E tests)

**엔드포인트**:
- `GET /api/v1/products` — 목록 (키워드, brandId, 정렬, 페이징)
- `GET /api/v1/products/{productId}` — 상세 (availableStock 포함)

**테스트**: `interfaces/api/product/ProductV1ApiE2ETest.java`

### 6-3. LikeV1Controller + LikeV1Dto (~7 E2E tests)

**엔드포인트**:
- `POST /api/v1/products/{productId}/likes` — 좋아요 추가 (멱등)
- `DELETE /api/v1/products/{productId}/likes` — 좋아요 제거 (멱등)
- `GET /api/v1/users/me/likes` — 내 좋아요 목록

**테스트**: `interfaces/api/like/LikeV1ApiE2ETest.java`

### 6-4. CartV1Controller + CartV1Dto (~10 E2E tests)

**엔드포인트**:
- `GET /api/v1/cart` — 장바구니 조회 (available/unavailableReason 포함)
- `POST /api/v1/cart/items` — 추가 (중복시 merge)
- `PATCH /api/v1/cart/items/{productId}` — 수량 변경
- `DELETE /api/v1/cart/items/{productId}` — 삭제 (멱등)

**테스트**: `interfaces/api/cart/CartV1ApiE2ETest.java`

### 6-5. OrderV1Controller + OrderV1Dto (~14 E2E tests)

**엔드포인트**:
- `POST /api/v1/orders` — DIRECT 주문 생성
- `POST /api/v1/orders/cart` — CART 주문 생성
- `GET /api/v1/orders` — 주문 목록 (기간 조회)
- `GET /api/v1/orders/{orderId}` — 주문 상세 (스냅샷 포함)
- `POST /api/v1/orders/{orderId}/cancel` — 주문 취소

**테스트**: `interfaces/api/order/OrderV1ApiE2ETest.java`
```
POST_directOrder_ShouldReturn201
POST_directOrder_InsufficientStock_ShouldReturn409
POST_directOrder_ProductNotOrderable_ShouldReturn409
POST_directOrder_PendingLimitExceeded_ShouldReturn409

POST_cartOrder_ShouldReturn201
POST_cartOrder_PartialFailure_ShouldReturn409_AllRolledBack
POST_cartOrder_EmptyItems_ShouldReturn400
POST_cartOrder_PendingLimitExceeded_ShouldReturn409

GET_orders_ShouldReturn200WithList
GET_orderDetail_ShouldReturn200WithSnapshots
GET_orderDetail_NotOwner_ShouldReturn404

POST_cancelOrder_ShouldReturn200
POST_cancelOrder_AlreadyCancelled_ShouldReturn200_Idempotent
POST_cancelOrder_Expired_ShouldReturn409
```

### Phase 6 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.brand.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.product.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.like.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.cart.*"
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.order.*"
```

**산출물: ~10개 소스 파일 (5 Controller + 5 DTO), ~42개 테스트**

---

## Phase 7: Interfaces — Admin API (Controller + DTO + E2E)

**목표**: 관리자용 API (`/api-admin/v1/...`) 컨트롤러와 DTO를 구현한다.
인증: `X-Loopers-Ldap: loopers.admin` 헤더

### 7-1. AdminBrandV1Controller + AdminBrandV1Dto (~6 E2E tests)

**엔드포인트**:
- `GET /api-admin/v1/brands` — 전체 목록 (HIDDEN/삭제 포함)
- `POST /api-admin/v1/brands` — 브랜드 생성
- `PUT /api-admin/v1/brands/{brandId}` — 브랜드 수정
- `DELETE /api-admin/v1/brands/{brandId}` — 브랜드 삭제

### 7-2. AdminProductV1Controller + AdminProductV1Dto (~8 E2E tests)

**엔드포인트**:
- `GET /api-admin/v1/products` — 전체 목록 (includeDeleted)
- `POST /api-admin/v1/products` — 상품 생성
- `PUT /api-admin/v1/products/{productId}` — 상품 수정 (brandId 변경불가)
- `DELETE /api-admin/v1/products/{productId}` — 상품 삭제
- `GET /api-admin/v1/products/{productId}/revisions` — 변경이력 목록/상세

### 7-3. AdminOrderV1Controller + AdminOrderV1Dto (~2 E2E tests)

**엔드포인트**:
- `GET /api-admin/v1/orders` — 전체 주문 목록
- `GET /api-admin/v1/orders/{orderId}` — 주문 상세

### 7-4. AdminCartV1Controller + AdminCartV1Dto (~1 E2E test)

**엔드포인트**:
- `GET /api-admin/v1/users/{userId}/cart` — 사용자 장바구니 조회

### 7-5. AdminStatsV1Controller + AdminStatsV1Dto (~5 E2E tests)

**엔드포인트**:
- `GET /api-admin/v1/stats/overview` — 주문 현황 요약
- `GET /api-admin/v1/stats/orders/daily` — 일별 주문 통계
- `GET /api-admin/v1/stats/products/top-liked` — 좋아요 상위 상품
- `GET /api-admin/v1/stats/products/top-ordered` — 주문 상위 상품
- `GET /api-admin/v1/stats/stocks/low` — 재고 부족 상품

### Phase 7 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.apiadmin.*"
```

**산출물: ~10개 소스 파일, ~22개 테스트**

---

## Phase 8: Batch (OrderExpiryScheduler)

**목표**: 주문 만료 스케줄러를 구현한다. 1분 주기로 PENDING_PAYMENT 만료 주문을 찾아 EXPIRED로 전환한다.

### 8-1. OrderExpiryScheduler (~5 tests)

#### RED
**파일**: `apps/commerce-api/src/test/java/com/loopers/batch/OrderExpirySchedulerTest.java`
```
@SpringBootTest @ActiveProfiles("test")

shouldExpire_PendingPayment_WhenExpiresAtPast
shouldReleaseStock_ForExpiredOrders
shouldRestoreCart_ForExpiredDirectOrders
shouldSkip_AlreadyCancelledOrExpired (CAS 실패 → no-op)
shouldBeIdempotent_WhenRunTwice
```

#### GREEN
**파일**: `apps/commerce-api/src/main/java/com/loopers/batch/OrderExpiryScheduler.java`
```java
@Component
@RequiredArgsConstructor
public class OrderExpiryScheduler {
    private final OrderService orderService;

    @Scheduled(fixedDelay = 60000) // 1분 주기
    public void expireOrders() {
        List<OrderModel> expired = orderService.findExpiredPendingOrders();
        for (OrderModel order : expired) {
            try {
                orderService.expireOrder(order.getOrderId());
            } catch (Exception e) {
                log.warn("주문 만료 처리 실패: orderId={}", order.getOrderId(), e);
            }
        }
    }
}
```

인덱스: `orders(status, expires_at)` 복합 인덱스 필요

### Phase 8 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.batch.*"
```

**산출물: 1개 소스 파일, ~5개 테스트**

---

## Phase 9: Integration + Refactoring

**목표**: 전체 흐름 통합 시나리오로 도메인 간 상호작용을 검증하고, 공통 로직을 리팩토링한다.

### 9-1. 전체 플로우 통합 테스트

**파일**: `apps/commerce-api/src/test/java/com/loopers/integration/FullOrderFlowIntegrationTest.java`
```
@SpringBootTest @ActiveProfiles("test") @Transactional

scenario1_DirectOrder_CreateAndCancel
  → 회원가입 → 브랜드등록 → 상품등록(재고10) → 바로주문(수량2)
  → 재고확인(available=8, reserved=2) → 취소
  → 재고확인(available=10, reserved=0) → 장바구니복원확인

scenario2_CartOrder_CreateAndCancel
  → 회원가입 → 상품2개 등록 → 장바구니담기 → 선택주문
  → 재고확인 → 취소 → 재고해제확인 → 장바구니유지확인

scenario3_OrderExpiry
  → 주문생성(expiresAt=과거) → 만료스케줄러실행
  → 상태EXPIRED확인 → 재고해제확인 → (DIRECT)장바구니복원확인

scenario4_ConcurrentOrderAndCancel
  → 주문생성 → 동시취소+만료 → 한쪽만 성공 (CAS)

scenario5_BrandDelete_CascadeToProducts
  → 브랜드삭제 → 상품소프트삭제확인 → 고객조회불가확인
  → 장바구니에서 available=false, reason=BRAND_DELETED 확인

scenario6_PendingLimitExceeded
  → 회원가입 → 주문3건 생성(모두 PENDING_PAYMENT)
  → 4번째 주문 시도 → ORDER_PENDING_LIMIT_EXCEEDED 확인
  → 1건 취소 → 다시 주문 가능 확인
```

### 9-2. 장바구니 복원 멱등성 테스트 (~3 tests)

**파일**: `apps/commerce-api/src/test/java/com/loopers/domain/order/OrderCartRestoreIdempotencyTest.java`
```
cancelDirectOrder_Twice_ShouldNotDuplicateCartItems
cancelDirectOrder_WhenCartItemAlreadyExists_ShouldMergeQuantity
expireDirectOrder_AfterManualCancel_ShouldNotRestoreAgain
```

### 9-3. 리팩터링

- Admin 인증 로직 공통화 (`X-Loopers-Ldap` 검증 인터셉터 또는 AOP)
- 페이징 응답 공통 DTO 추출
- UnavailableReason 계산 로직 유틸리티 추출

### 9-4. HTTP Client 파일 작성

`http/commerce-api/` 하위:
- `user-v1.http`, `brand-v1.http`, `product-v1.http`
- `like-v1.http`, `cart-v1.http`, `order-v1.http`
- `admin-brand-v1.http`, `admin-product-v1.http`
- `admin-order-v1.http`, `admin-stats-v1.http`

### Phase 9 검증
```bash
./gradlew :apps:commerce-api:test --tests "com.loopers.integration.*"
./gradlew clean build  # 전체 빌드 최종 확인
```

**산출물: ~2개 테스트 파일 + 리팩토링 + HTTP 파일, ~9개 테스트**

---

## 테스트 요약

| Phase | 카테고리 | 파일 수 | 예상 케이스 수 |
|-------|----------|---------|---------------|
| 0 | 인프라 기반 (BaseStringIdEntity + Enum + ErrorType) | 6 | ✅ 19 |
| 1 | User 전 레이어 (Facade 없이 Service 직접) | 4 | ✅ 45 |
| 2 | Domain Model + Info DTO 단위 | ~9 | ~73 |
| 3 | Domain Service 단위 — Mock (Info 반환 포함) | ~7 | ~91 |
| 4 | Infrastructure 통합 + 동시성 | ~9 | ~44 |
| 5 | Application Facade 단위 (**3개만**: Product, Cart, Order) | ~3 | ~15 |
| 6 | E2E 고객 API (단순 도메인: Service 직접 호출) | ~5 | ~42 |
| 7 | E2E 관리자 API | ~5 | ~22 |
| 8 | Batch | ~1 | ~5 |
| 9 | 통합 플로우 + 멱등성 | ~2 | ~9 |
| **합계** | | **~51** | **~365** |

---

## Verification

### 테스트 실행
```bash
./gradlew test
```

### 빌드 검증
```bash
./gradlew clean build
```
