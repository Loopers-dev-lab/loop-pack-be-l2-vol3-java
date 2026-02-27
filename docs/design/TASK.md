# 감성 이커머스 MVP - TDD 구현 체크리스트 (레이어별)

> **구현 전략**: 도메인별 수직 구현 → **레이어별 수평 구현**으로 변경
> 각 Phase에서 하나의 레이어를 모든 도메인에 걸쳐 구현한다.

---

## Phase 0: 인프라 기반 ✅ 완료

### 0-1. BaseStringIdEntity
- [x] **RED** `BaseStringIdEntityTest.java` 작성
  - [x] create_ShouldGenerateUuidId
  - [x] prePersist_ShouldSetTimestamps
  - [x] softDelete_ShouldSetDelYnYAndDeletedAt
  - [x] softDelete_WhenAlreadyDeleted_ShouldBeIdempotent
  - [x] restore_ShouldSetDelYnNAndClearDeletedAt
  - [x] restore_WhenNotDeleted_ShouldBeIdempotent
  - [x] isDeleted_ShouldReflectDelYnStatus
  - [x] delYnAndDeletedAt_ShouldAlwaysBeConsistent
- [x] **GREEN** `BaseStringIdEntity.java` 구현
- [x] **REFACTOR** 코드 정리

### 0-2. Enum 정의
- [x] **RED** Enum 테스트 작성
  - [x] DisplayStatusTest
  - [x] ProductSaleStatusTest (isOrderable 포함)
  - [x] OrderStatusTest (canCancel 포함)
  - [x] OrderTypeTest
- [x] **GREEN** Enum 구현
  - [x] `DisplayStatus` (ACTIVE, HIDDEN)
  - [x] `ProductSaleStatus` (ON_SALE, TEMP_SOLD_OUT, STOPPED)
  - [x] `OrderType` (DIRECT, CART)
  - [x] `OrderStatus` (PENDING_PAYMENT, CANCELLED, EXPIRED)
  - [x] `ProductRevisionAction` (CREATE, UPDATE, HIDE, SALE_STATUS_CHANGE, DELETE, RESTORE)
  - [x] `RestoreReason` (USER_CANCELLED, EXPIRED, PAYMENT_FAILED, PG_CANCELLED)
  - [x] `RestoreTriggerSource` (CANCEL_API, PG_WEBHOOK, EXPIRE_JOB, MANUAL)
  - [x] `UnavailableReason` (DELETED, HIDDEN, BRAND_DELETED, BRAND_HIDDEN, STOPPED, TEMP_SOLD_OUT, OUT_OF_STOCK, INVALID_QUANTITY)

### 0-3. ErrorType 확장
- [x] **RED** `ErrorTypeExtensionTest.java` 작성
- [x] **GREEN** `ErrorType.java`에 새 에러 추가
  - [x] USER_NOT_FOUND, DUPLICATE_USER_ID
  - [x] BRAND_NOT_FOUND, DUPLICATE_BRAND
  - [x] PRODUCT_NOT_FOUND, PRODUCT_NOT_ORDERABLE, INVALID_STOCK_UPDATE
  - [x] STOCK_NOT_ENOUGH
  - [x] LIKE_PRODUCT_NOT_FOUND
  - [x] CART_ITEM_NOT_FOUND, CART_LIMIT_EXCEEDED, CART_STOCK_EXCEEDED
  - [x] ORDER_NOT_FOUND, ORDER_NOT_CANCELLABLE, ORDER_NOT_CREATABLE, ORDER_ITEM_EMPTY, ORDER_PENDING_LIMIT_EXCEEDED
  - [x] ADMIN_UNAUTHORIZED

### 0-4. 빌드 확인
- [x] `./gradlew compileTestJava` 통과
- [ ] `./gradlew test` 통과

---

## Phase 1: User 도메인 ✅ 완료

> 기존 Member는 레거시 유지. User는 BaseStringIdEntity(UUID PK) + loginId(unique) 구조.

### 1-1. UserModel
- [x] **RED** `UserModelTest.java` 작성 (~19 케이스)
  - [x] 생성 검증 (유효/무효 loginId, 기본값, BaseStringIdEntity 상속)
  - [x] 비밀번호 검증 (길이, 문자, 생년월일 포함)
  - [x] 이름 마스킹 (일반, 1자)
  - [x] 비밀번호 업데이트 (유효/무효)
  - [x] 소프트 삭제/복원 (멱등성)
- [x] **GREEN** `UserModel.java` 구현
- [x] **REFACTOR**

### 1-2. UserService
- [x] **RED** `UserServiceTest.java` 작성 (~11 케이스)
  - [x] register (성공, 중복 loginId)
  - [x] findById (성공, 미존재)
  - [x] findByLoginId (성공, 미존재)
  - [x] authenticate (성공, 비밀번호 불일치)
  - [x] changePassword (성공, 현재 비밀번호 불일치, 동일 비밀번호)
- [x] **GREEN** 구현
  - [x] `UserRepository.java` (interface)
  - [x] `PasswordEncoder.java` (interface)
  - [x] `UserService.java`
- [x] **REFACTOR**

### 1-3. UserRepository 통합
- [x] **RED** `UserRepositoryImplTest.java` 작성 (~7 케이스)
  - [x] save (UUID PK 생성)
  - [x] findById (존재/미존재)
  - [x] findByLoginId (존재/미존재)
  - [x] existsByLoginId (존재/미존재)
- [x] **GREEN** 구현
  - [x] `UserJpaRepository.java`
  - [x] `UserRepositoryImpl.java`
  - [x] `BCryptPasswordEncoder.java`

### 1-4. UserInfo DTO (domain 패키지)
- [x] **GREEN** 구현
  - [x] `domain/user/UserInfo.java` — `static from(UserModel)` (Facade 제거, Service가 직접 반환)

### 1-5. User API E2E
- [x] **RED** `UserV1ApiE2ETest.java` 작성 (~8 케이스)
  - [x] POST /api/v1/users (회원가입: 성공, 중복, 유효성)
  - [x] GET /api/v1/users/me (인증 성공/실패)
  - [x] PATCH /api/v1/users/me/password (성공/실패)
- [x] **GREEN** 구현
  - [x] `UserV1Controller.java` + `UserV1Dto.java`

### 1-6. 빌드 확인
- [x] `./gradlew compileTestJava` 통과
- [ ] `./gradlew test` 통과

---

## Phase 2: Domain Model (모든 엔티티 + Info DTO + 단위 테스트) ✅ 코드 작성 완료

> 나머지 모든 JPA Entity, 복합 PK 클래스, **Info DTO**를 구현하고 단위 테스트로 검증한다.
> **Info DTO는 `domain/` 패키지에 위치**한다. 외부 의존성 없이 모델의 생성/검증/비즈니스 메서드만 테스트한다.

### 2-1. BrandModel (~15 tests)
- [x] **RED** `BrandModelTest.java` 작성
  - [x] 생성 검증 (유효/무효 brandName, 기본값 ACTIVE/delYn=N)
  - [x] 상태 전이 (hide, activate)
  - [x] 소프트 삭제/복원 (멱등성)
  - [x] isVisibleForCustomer 조합 검증
  - [x] updateInfo 검증
- [x] **GREEN** `BrandModel.java` 구현
- [x] **GREEN** `BrandInfo.java` 구현 (`domain/brand/` 패키지, `static from(BrandModel)`)
- [x] **REFACTOR**

### 2-2. ProductModel (~17 tests)
- [x] **RED** `ProductModelTest.java` 작성
  - [x] 생성 검증 (유효/무효 name/brandId/price, 기본값 ACTIVE/ON_SALE/revisionSeq=0)
  - [x] 상태 전이 (displayStatus, saleStatus)
  - [x] isOrderable 조합 검증 (HIDDEN, TEMP_SOLD_OUT, STOPPED, deleted)
  - [x] updateInfo + revisionSeq 증가
- [x] **GREEN** `ProductModel.java` 구현
- [x] **GREEN** `ProductInfo.java` 구현 (`domain/product/` 패키지, `static from(ProductModel, ProductStockModel)`)
- [x] **REFACTOR**

### 2-3. ProductStockModel (~8 tests)
- [x] **RED** `ProductStockModelTest.java` 작성
  - [x] 생성 검증 (유효/음수 onHand, 초기 reserved=0)
  - [x] getAvailableQty, canHold (성공/실패)
  - [x] updateOnHand (성공/reserved 이상만 허용)
- [x] **GREEN** `ProductStockModel.java` 구현
- [x] **REFACTOR**

### 2-4. ProductRevisionModel (~2 tests)
- [x] **RED** `ProductRevisionModelTest.java` 작성
  - [x] create 성공
  - [x] CREATE action시 beforeSnapshot=null
- [x] **GREEN** 구현
  - [x] `ProductRevisionModel.java`
  - [x] `ProductRevisionId.java` (복합 PK)

### 2-5. LikeModel (~4 tests)
- [x] **RED** `LikeModelTest.java` 작성
  - [x] create 성공
  - [x] null userId/productId 실패
  - [x] createdAt 설정 확인
- [x] **GREEN** 구현
  - [x] `LikeModel.java` (복합 PK)
  - [x] `LikeId.java`
  - [x] `LikeInfo.java` (`domain/like/` 패키지, `static from(LikeModel)`)

### 2-6. CartItemModel (~7 tests)
- [x] **RED** `CartItemModelTest.java` 작성
  - [x] 생성 검증 (유효/무효 수량 0/음수)
  - [x] changeQuantity (성공/실패)
  - [x] mergeQuantity
- [x] **GREEN** 구현
  - [x] `CartItemModel.java` (복합 PK)
  - [x] `CartItemId.java`
  - [x] `CartInfo.java` (`domain/cart/` 패키지, `static from(CartItemModel, ProductModel, BrandModel, ProductStockModel)`)

### 2-7. OrderModel (~15 tests)
- [x] **RED** `OrderModelTest.java` 작성
  - [x] 생성 검증 (상태 PENDING_PAYMENT, expiresAt 15분)
  - [x] cancel 상태 전이 (성공, 멱등, EXPIRED시 불가)
  - [x] expire 상태 전이 (성공, 멱등, CANCELLED시 불가)
  - [x] canCancel, isExpired 조건별
- [x] **GREEN** `OrderModel.java` 구현
- [x] **GREEN** `OrderInfo.java` 구현 (`domain/order/` 패키지, `static from(OrderModel, List<OrderItemModel>)`)

### 2-8. OrderItemModel (~3 tests)
- [x] **RED** `OrderItemModelTest.java` 작성
  - [x] 생성 + 스냅샷 캡처
  - [x] quantity=0 실패
  - [x] getSubtotal = unitPrice * quantity
- [x] **GREEN** 구현
  - [x] `OrderItemModel.java`
  - [x] `OrderItemId.java` (복합 PK)

### 2-9. OrderCartRestoreModel (~2 tests)
- [x] **RED** `OrderCartRestoreModelTest.java` 작성
  - [x] create 성공
  - [x] restoredAt 설정 확인
- [x] **GREEN** `OrderCartRestoreModel.java` 구현

### 2-10. 빌드 확인
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.cart.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.*"` 통과

---

## Phase 3: Domain Service + Repository Interface (Mock 단위 테스트) ✅ 코드 작성 완료

> 모든 도메인 Service와 Repository 인터페이스를 구현한다.
> **단순 도메인의 Service는 Info를 직접 반환**한다.
> Service 테스트는 Mockito로 Repository를 Mock하여 비즈니스 로직만 검증한다.

### 3-1. BrandService + BrandRepository (~10 tests)
- [x] **RED** `BrandServiceTest.java` 작성
  - [x] createBrand (성공)
  - [x] findById (성공, BRAND_NOT_FOUND)
  - [x] findVisibleById (HIDDEN/deleted 실패)
  - [x] findAllVisibleBrands (필터링, 키워드)
  - [x] updateBrand (성공)
  - [x] deleteBrand (소프트삭제, 멱등)
- [x] **GREEN** 구현
  - [x] `BrandRepository.java` (interface)
  - [x] `BrandService.java`
- [x] **REFACTOR**

### 3-2. StockService + ProductStockRepository (~5 tests)
- [x] **RED** `StockServiceTest.java` 작성
  - [x] hold (성공, STOCK_NOT_ENOUGH)
  - [x] release (성공, 실패)
  - [x] commit (Phase2 대비)
- [x] **GREEN** 구현
  - [x] `ProductStockRepository.java` (interface — CAS 메서드)
  - [x] `StockService.java`
- [x] **REFACTOR**

### 3-3. ProductService + ProductRepository + ProductRevisionRepository (~19 tests)
- [x] **RED** `ProductServiceTest.java` 작성
  - [x] createProduct (성공, Revision CREATE, Stock 생성)
  - [x] findById/findOrderableById (성공, 실패)
  - [x] findAllForCustomer (필터링, 키워드, brandId)
  - [x] updateProduct (Revision 생성, revisionSeq 증가)
  - [x] deleteProduct (소프트삭제 + Revision, 멱등)
  - [x] softDeleteByBrandId (브랜드 연쇄 삭제)
  - [x] changeSaleStatus (Revision 생성)
  - [x] findRevisions (목록/상세)
- [x] **GREEN** 구현
  - [x] `ProductRepository.java` (interface)
  - [x] `ProductRevisionRepository.java` (interface)
  - [x] `ProductService.java`
- [x] **REFACTOR**

### 3-4. LikeService + LikeRepository (~7 tests)
- [x] **RED** `LikeServiceTest.java` 작성
  - [x] addLike (신규, 멱등, 상품 미존재)
  - [x] removeLike (존재, 멱등)
  - [x] getMyLikes, countByProductId
- [x] **GREEN** 구현
  - [x] `LikeRepository.java` (interface)
  - [x] `LikeService.java`
- [x] **REFACTOR**

### 3-5. CartService + CartItemRepository (~17 tests)
- [x] **RED** `CartServiceTest.java` 작성
  - [x] addItem (신규, 중복 병합, 비주문 가능, 재고 초과)
  - [x] changeQuantity (성공, 미존재, 재고 초과)
  - [x] removeItem (성공, 멱등)
  - [x] getCart + UnavailableReason (DELETED, HIDDEN, BRAND_DELETED, BRAND_HIDDEN, STOPPED, TEMP_SOLD_OUT, OUT_OF_STOCK)
  - [x] restoreFromOrder (생성, 기존 항목 병합)
- [x] **GREEN** 구현
  - [x] `CartItemRepository.java` (interface)
  - [x] `CartService.java`
- [x] **REFACTOR**

### 3-6. OrderService + Order Repositories (~28 tests)
- [x] **RED** `OrderServiceTest.java` 작성
  - [x] 바로 주문 (DIRECT) — 6 케이스
    - [x] 정상 생성 (검증+예약+저장+스냅샷)
    - [x] 상품 주문불가
    - [x] 재고 부족
    - [x] orderType=DIRECT
    - [x] totalAmount 계산
    - [x] **PENDING 3건 초과 → ORDER_PENDING_LIMIT_EXCEEDED**
  - [x] 장바구니 주문 (CART) — 4 케이스
    - [x] 전체 성공
    - [x] 빈 선택
    - [x] 부분 실패 → 전체 롤백
    - [x] **PENDING 3건 초과 → ORDER_PENDING_LIMIT_EXCEEDED**
  - [x] 주문 취소 — 9 케이스
    - [x] CAS 상태 전이
    - [x] 재고 해제
    - [x] 본인 아닌 주문
    - [x] 이미 취소 (멱등)
    - [x] 만료 주문 불가
    - [x] DIRECT → 장바구니 복원
    - [x] 복원 이력 기록
    - [x] 2차 취소 중복 복원 방지 (멱등)
    - [x] CART → 장바구니 유지
  - [x] 주문 만료 (배치) — 4 케이스
    - [x] CAS 상태 전이
    - [x] 재고 해제
    - [x] DIRECT → 장바구니 복원
    - [x] 이미 만료/취소 → skip
  - [x] 조회 — 2 케이스
- [x] **GREEN** 구현
  - [x] `OrderRepository.java` (interface — CAS, countByStatus)
  - [x] `OrderItemRepository.java` (interface)
  - [x] `OrderCartRestoreRepository.java` (interface)
  - [x] `OrderService.java`
- [x] **REFACTOR**

### 3-7. StatsService + StatsRepository (~5 tests)
- [x] **RED** `StatsServiceTest.java` 작성
  - [x] getOverview (주문 상태별 건수)
  - [x] getDailyOrderStats (일별 집계)
  - [x] getTopLikedProducts (좋아요 TOP N)
  - [x] getTopOrderedProducts (주문 TOP N)
  - [x] getLowStockProducts (저재고 목록)
- [x] **GREEN** 구현
  - [x] `StatsRepository.java` (interface)
  - [x] `StatsInfo.java` (`domain/stats/` 패키지)
  - [x] `StatsService.java` — **Info를 직접 반환**

### 3-8. 빌드 확인
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.brand.BrandServiceTest"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.*ServiceTest"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.like.LikeServiceTest"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.cart.CartServiceTest"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.order.OrderServiceTest"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.stats.StatsServiceTest"` 통과

---

## Phase 4: Infrastructure (JpaRepository + RepositoryImpl + 통합 테스트) ✅ 코드 작성 완료

> 모든 JpaRepository와 RepositoryImpl을 구현하고 Testcontainers(MySQL) 통합 테스트로 검증한다.
> CAS 쿼리와 동시성도 이 단계에서 검증한다. Docker 실행 필요.

### 4-1. Brand Infrastructure (~5 tests)
- [x] **RED** `BrandRepositoryImplTest.java` 작성
  - [x] save (UUID PK 생성)
  - [x] findById (존재/미존재)
  - [x] findAllByDelYnAndDisplayStatus (필터링)
  - [x] findAllByKeyword (부분 일치)
- [x] **GREEN** 구현
  - [x] `BrandJpaRepository.java`
  - [x] `BrandRepositoryImpl.java`

### 4-2. Product Infrastructure (~6 tests)
- [x] **RED** `ProductRepositoryImplTest.java` 작성
  - [x] save (UUID PK 생성)
  - [x] findById (존재/미존재)
  - [x] findAllForCustomer (ACTIVE+del_yn=N 필터)
  - [x] findAllForCustomer (키워드/brandId 필터)
- [x] **GREEN** 구현
  - [x] `ProductJpaRepository.java`
  - [x] `ProductRepositoryImpl.java`

### 4-3. ProductStock CAS Infrastructure (~4 tests)
- [x] **RED** `ProductStockRepositoryImplTest.java` 작성
  - [x] reserveStock CAS (성공: affectedRows=1 / 실패: affectedRows=0)
  - [x] releaseStock CAS
  - [x] confirmStock CAS
- [x] **GREEN** 구현
  - [x] `ProductStockJpaRepository.java` (@Modifying @Query CAS UPDATE)
  - [x] `ProductStockRepositoryImpl.java`

### 4-4. ProductRevision Infrastructure
- [x] **GREEN** 구현
  - [x] `ProductRevisionJpaRepository.java`
  - [x] `ProductRevisionRepositoryImpl.java`

### 4-5. 재고 동시성 테스트 (~2 tests)
- [x] **RED** `StockConcurrencyTest.java` 작성
  - [x] concurrentHold_ShouldNotOversell (stock=10, 20스레드 → 정확히 10성공)
  - [x] concurrentHoldAndRelease_ShouldMaintainConsistency

### 4-6. Like Infrastructure (~6 tests)
- [x] **RED** `LikeRepositoryImplTest.java` 작성
  - [x] save (복합 PK)
  - [x] findById (존재/미존재)
  - [x] delete (물리 삭제)
  - [x] findAllByUserId
  - [x] countByProductId
- [x] **GREEN** 구현
  - [x] `LikeJpaRepository.java`
  - [x] `LikeRepositoryImpl.java`

### 4-7. Cart Infrastructure (~6 tests)
- [x] **RED** `CartItemRepositoryImplTest.java` 작성
  - [x] save (복합 PK)
  - [x] findById (존재/미존재)
  - [x] delete
  - [x] findAllByUserId
  - [x] save (기존 항목 업데이트)
- [x] **GREEN** 구현
  - [x] `CartItemJpaRepository.java`
  - [x] `CartItemRepositoryImpl.java`

### 4-8. Order Infrastructure + CAS (~6 tests)
- [x] **RED** `OrderRepositoryImplTest.java` 작성
  - [x] save (UUID PK)
  - [x] findById, findByIdAndUserId
  - [x] casUpdateStatus (성공: affectedRows=1 / 실패: affectedRows=0)
  - [x] findExpiredPendingOrders (만료 대상만)
- [x] **GREEN** 구현
  - [x] `OrderJpaRepository.java` (@Modifying @Query CAS)
  - [x] `OrderRepositoryImpl.java`
  - [x] `OrderItemJpaRepository.java`
  - [x] `OrderItemRepositoryImpl.java`
  - [x] `OrderCartRestoreJpaRepository.java`
  - [x] `OrderCartRestoreRepositoryImpl.java`

### 4-9. Stats Infrastructure (QueryDSL) (~5 tests)
- [x] **RED** `StatsRepositoryImplTest.java` 작성
  - [x] getOverview (상태별 COUNT)
  - [x] getDailyOrderStats (GROUP BY date)
  - [x] getTopLikedProducts (JOIN + 집계)
  - [x] getTopOrderedProducts (JOIN + 집계)
  - [x] getLowStockProducts (on_hand - reserved < threshold)
- [x] **GREEN** `StatsRepositoryImpl.java` 구현 (JPAQueryFactory)

### 4-10. 빌드 확인
- [x] `./gradlew :apps:commerce-api:compileTestJava` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.domain.product.StockConcurrencyTest"` 통과

---

## Phase 5: Application (Facade — 복잡한 도메인만 + 단위 테스트) ✅ 코드 작성 완료

> **여러 서비스를 조합하는 복잡한 도메인만** Facade를 구현한다.
> 단순 도메인(User, Brand, Like, Stats, Example)은 Phase 3에서 Service가 Info를 직접 반환하므로 Facade 불필요.
> **9개 Facade → 3개로 축소**. 상세: [07-facade-analysis.md](./07-facade-analysis.md)

### 5-1. ProductFacade (~6 tests)
- [x] **RED** `ProductFacadeTest.java` 작성
  - [x] getProductsForCustomer → ProductInfo 리스트
  - [x] getProductDetailForCustomer → ProductInfo (availableStock 포함)
  - [x] createProduct → ProductInfo
  - [x] updateProduct → ProductInfo
  - [x] deleteProduct → 서비스 호출
  - [x] getRevisions → RevisionInfo 리스트
- [x] **GREEN** 구현
  - [x] `ProductFacade.java` (ProductService + StockService 조합)

### 5-2. CartFacade (~4 tests)
- [x] **RED** `CartFacadeTest.java` 작성
  - [x] getCart → CartInfo 리스트 (available/unavailableReason 포함)
  - [x] addItem → 서비스 호출
  - [x] changeQuantity → 서비스 호출
  - [x] removeItem → 서비스 호출
- [x] **GREEN** 구현
  - [x] `CartFacade.java` (CartService + UserService 조합)

### 5-3. OrderFacade (~5 tests)
- [x] **RED** `OrderFacadeTest.java` 작성
  - [x] createDirectOrder → OrderInfo
  - [x] createCartOrder → OrderInfo
  - [x] cancelOrder → 서비스 호출
  - [x] getOrders → OrderInfo 리스트
  - [x] getOrderDetail → OrderInfo (스냅샷 포함)
- [x] **GREEN** 구현
  - [x] `OrderFacade.java` (OrderService + UserService 조합)

### 5-4. 빌드 확인
- [x] `./gradlew :apps:commerce-api:compileTestJava` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.application.*"` 통과

---

## Phase 6: Interfaces — Customer API (Controller + DTO + E2E)

> 고객용 API (`/api/v1/...`) 컨트롤러와 DTO를 구현한다.
> E2E 테스트(@SpringBootTest + MockMvc)로 HTTP 요청~응답 전체 흐름을 검증한다.
>
> **의존성 규칙**: 단순 도메인(Brand, Like) → Service 직접 호출, 복잡한 도메인(Product, Cart, Order) → Facade 호출

### 6-1. BrandV1Controller + BrandV1Dto (~5 E2E tests)
- [ ] **RED** `BrandV1ApiE2ETest.java` 작성
  - [ ] GET /api/v1/brands → 200 (목록)
  - [ ] GET /api/v1/brands?q=keyword → 필터링
  - [ ] GET /api/v1/brands/{brandId} → 200
  - [ ] GET /api/v1/brands/{brandId} → 404 (미존재)
  - [ ] GET /api/v1/brands/{brandId} → 404 (HIDDEN)
- [ ] **GREEN** 구현
  - [ ] `BrandV1Controller.java`
  - [ ] `BrandV1Dto.java`

### 6-2. ProductV1Controller + ProductV1Dto (~6 E2E tests)
- [ ] **RED** `ProductV1ApiE2ETest.java` 작성
  - [ ] GET /api/v1/products → 200 (목록)
  - [ ] GET /api/v1/products?q=keyword → 필터링
  - [ ] GET /api/v1/products?brandId=... → 필터링
  - [ ] GET /api/v1/products/{productId} → 200 (availableStock 포함)
  - [ ] GET /api/v1/products/{productId} → 404 (미존재)
  - [ ] GET /api/v1/products/{productId} → 404 (삭제됨)
- [ ] **GREEN** 구현
  - [ ] `ProductV1Controller.java`
  - [ ] `ProductV1Dto.java`

### 6-3. LikeV1Controller + LikeV1Dto (~7 E2E tests)
- [ ] **RED** `LikeV1ApiE2ETest.java` 작성
  - [ ] POST /api/v1/products/{productId}/likes → 200 (등록)
  - [ ] POST (이미 좋아요) → 200 (멱등)
  - [ ] DELETE /api/v1/products/{productId}/likes → 200 (취소)
  - [ ] DELETE (없는 좋아요) → 200 (멱등)
  - [ ] GET /api/v1/users/me/likes → 200 (목록)
  - [ ] POST (인증 없이) → 401
  - [ ] POST (상품 미존재) → 404
- [ ] **GREEN** 구현
  - [ ] `LikeV1Controller.java`
  - [ ] `LikeV1Dto.java`

### 6-4. CartV1Controller + CartV1Dto (~10 E2E tests)
- [ ] **RED** `CartV1ApiE2ETest.java` 작성
  - [ ] GET /api/v1/cart → 200 (available/unavailableReason 포함)
  - [ ] GET (비주문가능 상품 포함) → available=false 확인
  - [ ] POST /api/v1/cart/items → 200 (추가)
  - [ ] POST (중복 상품) → 수량 병합
  - [ ] POST (비주문 가능) → 409
  - [ ] POST (재고 초과) → 400
  - [ ] PATCH /api/v1/cart/items/{productId} → 200 (수량 변경)
  - [ ] PATCH (미존재) → 404
  - [ ] DELETE /api/v1/cart/items/{productId} → 200 (삭제)
  - [ ] DELETE (미존재) → 200 (멱등)
- [ ] **GREEN** 구현
  - [ ] `CartV1Controller.java`
  - [ ] `CartV1Dto.java`

### 6-5. OrderV1Controller + OrderV1Dto (~14 E2E tests)
- [ ] **RED** `OrderV1ApiE2ETest.java` 작성
  - [ ] POST /api/v1/orders (DIRECT) → 201
  - [ ] POST (DIRECT, 재고부족) → 409
  - [ ] POST (DIRECT, 상품불가) → 409
  - [ ] POST (DIRECT, PENDING 초과) → 409
  - [ ] POST /api/v1/orders/cart (CART) → 201
  - [ ] POST (CART, 부분실패) → 409 + 롤백
  - [ ] POST (CART, 빈 선택) → 400
  - [ ] POST (CART, PENDING 초과) → 409
  - [ ] GET /api/v1/orders → 200 (목록)
  - [ ] GET /api/v1/orders/{orderId} → 200 (스냅샷 포함)
  - [ ] GET (본인 아닌 주문) → 404
  - [ ] POST /api/v1/orders/{orderId}/cancel → 200
  - [ ] POST (이미 취소) → 200 (멱등)
  - [ ] POST (만료 주문) → 409
- [ ] **GREEN** 구현
  - [ ] `OrderV1Controller.java`
  - [ ] `OrderV1Dto.java`

### 6-6. 빌드 확인
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.brand.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.product.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.like.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.cart.*"` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.order.*"` 통과

---

## Phase 7: Interfaces — Admin API (Controller + DTO + E2E) ✅ 코드 작성 완료

> 관리자용 API (`/api-admin/v1/...`) 컨트롤러와 DTO를 구현한다.
> 인증: `X-Loopers-Ldap: loopers.admin` 헤더 → Phase 9에서 `AdminAuthInterceptor`로 공통화 완료

### 7-1. AdminBrandV1Controller + AdminBrandV1Dto (~6 E2E tests)
- [x] **RED** `AdminBrandV1ApiE2ETest.java` 작성
  - [x] POST /api-admin/v1/brands → 200 (생성)
  - [x] POST (빈 이름) → 400
  - [x] PUT /api-admin/v1/brands/{brandId} → 200 (수정)
  - [x] DELETE /api-admin/v1/brands/{brandId} → 200 (삭제)
  - [x] GET /api-admin/v1/brands → 200 (HIDDEN/삭제 포함)
  - [x] GET (삭제된 브랜드) → 200 (관리자 접근 가능)
- [x] **GREEN** 구현
  - [x] `AdminBrandV1Controller.java`
  - [x] `AdminBrandV1Dto.java`

### 7-2. AdminProductV1Controller + AdminProductV1Dto (~8 E2E tests)
- [x] **RED** `AdminProductV1ApiE2ETest.java` 작성
  - [x] POST /api-admin/v1/products → 200 (생성)
  - [x] POST (미존재 브랜드) → 404
  - [x] PUT /api-admin/v1/products/{productId} → 200 (수정)
  - [x] DELETE /api-admin/v1/products/{productId} → 200 (삭제)
  - [x] GET /api-admin/v1/products?includeDeleted=true → 200
  - [x] GET /api-admin/v1/products/{productId}/revisions → 200
  - [x] GET /api-admin/v1/products/{productId}/revisions/{seq} → 200
- [x] **GREEN** 구현
  - [x] `AdminProductV1Controller.java`
  - [x] `AdminProductV1Dto.java`

### 7-3. AdminOrderV1Controller + AdminOrderV1Dto (~2 E2E tests)
- [x] **RED** `AdminOrderV1ApiE2ETest.java` 작성
  - [x] GET /api-admin/v1/orders → 200 (전체 목록)
  - [x] GET /api-admin/v1/orders/{orderId} → 200 (상세)
- [x] **GREEN** 구현
  - [x] `AdminOrderV1Controller.java`
  - [x] `AdminOrderV1Dto.java`

### 7-4. AdminCartV1Controller + AdminCartV1Dto (~1 E2E test)
- [x] **RED** `AdminCartV1ApiE2ETest.java` 작성
  - [x] GET /api-admin/v1/users/{userId}/cart → 200
- [x] **GREEN** 구현
  - [x] `AdminCartV1Controller.java`
  - [x] `AdminCartV1Dto.java`

### 7-5. AdminStatsV1Controller + AdminStatsV1Dto (~5 E2E tests)
- [x] **RED** `AdminStatsV1ApiE2ETest.java` 작성
  - [x] GET /api-admin/v1/stats/overview → 200
  - [x] GET /api-admin/v1/stats/orders/daily → 200
  - [x] GET /api-admin/v1/stats/products/top-liked → 200
  - [x] GET /api-admin/v1/stats/products/top-ordered → 200
  - [x] GET /api-admin/v1/stats/stocks/low → 200
- [x] **GREEN** 구현
  - [x] `AdminStatsV1Controller.java`
  - [x] `AdminStatsV1Dto.java`

### 7-6. 빌드 확인
- [x] `./gradlew :apps:commerce-api:compileTestJava` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.apiadmin.*"` 통과

---

## Phase 8: Batch (OrderExpiryScheduler) ✅ 코드 작성 완료

### 8-1. OrderExpiryScheduler (~5 tests)
- [x] **RED** `OrderExpirySchedulerTest.java` 작성
  - [x] 만료 대상 EXPIRED 전환
  - [x] 재고 해제 확인
  - [x] DIRECT 주문 장바구니 복원
  - [x] 이미 취소/만료 → skip (CAS)
  - [x] 2회 실행 멱등성
- [x] **GREEN** `OrderExpiryScheduler.java` 구현 (@Scheduled, 1분 주기)
- [x] `CommerceApiApplication.java`에 `@EnableScheduling` 추가
- [ ] DB 인덱스 추가: `orders(status, expires_at)`

### 8-2. 빌드 확인
- [x] `./gradlew :apps:commerce-api:compileTestJava` 통과
- [ ] `./gradlew :apps:commerce-api:test --tests "com.loopers.batch.*"` 통과

---

## Phase 9: Integration + Refactoring ✅ 코드 작성 완료

### 9-1. 전체 플로우 통합 테스트 (~4 시나리오)
- [x] **RED** `FullOrderFlowIntegrationTest.java` 작성
  - [x] scenario1: 바로주문 → 취소 → 재고해제 + 장바구니복원
  - [x] scenario2: 장바구니주문 → 취소 → 재고해제 + 장바구니유지
  - [x] scenario5: 브랜드삭제 → 상품연쇄삭제 → 장바구니 unavailable
  - [x] scenario6: **PENDING 제한 초과 → 취소 후 재주문 가능**

### 9-2. 장바구니 복원 멱등성 테스트 (~3 tests)
- [x] **RED** `OrderCartRestoreIdempotencyTest.java` 작성
  - [x] 2회 취소 → 중복 복원 방지
  - [x] 기존 장바구니 항목 존재 시 수량 병합
  - [x] 취소 후 만료 → 재복원 방지

### 9-3. 리팩터링
- [x] Admin 인증 로직 공통화 (`AdminAuthInterceptor` + `WebMvcConfig` — `/api-admin/**` 인터셉터)
- [x] 페이징 응답 공통 DTO 추출 (`PageResponse<T>`)
- [x] UnavailableReason 계산 로직 유틸리티 추출 (`ProductAvailabilityChecker`)
- [x] Admin Controller 5개에서 `validateAdmin()` + `@RequestHeader ldap` 파라미터 제거

### 9-4. HTTP Client 파일 작성
- [x] `http/commerce-api/user-v1.http`
- [x] `http/commerce-api/brand-v1.http`
- [x] `http/commerce-api/product-v1.http`
- [x] `http/commerce-api/like-v1.http`
- [x] `http/commerce-api/cart-v1.http`
- [x] `http/commerce-api/order-v1.http`
- [x] `http/commerce-api/admin-brand-v1.http`
- [x] `http/commerce-api/admin-product-v1.http`
- [x] `http/commerce-api/admin-order-v1.http`
- [x] `http/commerce-api/admin-stats-v1.http`

### 9-5. 최종 검증
- [x] `./gradlew :apps:commerce-api:compileTestJava` 통과
- [ ] `./gradlew clean build` 통과
- [ ] 전체 테스트 통과 확인

---

## 진행 현황 요약

> Facade 개선안 반영: 9개 → 3개 (Product, Cart, Order만). Info DTO는 domain 패키지로 이동.

| Phase | 레이어 | 상태 | 소스 파일 | 테스트 수 |
|-------|--------|------|----------|----------|
| Phase 0 | 인프라 기반 | ✅ 완료 | 10 | 19 |
| Phase 1 | User 전체 (Facade 없이) | ✅ 완료 | 9 | 45 |
| Phase 2 | Domain Model + Info DTO | ✅ 코드 작성 완료 | ~24 | ~73 |
| Phase 3 | Domain Service (Info 반환) | ✅ 코드 작성 완료 | ~17 | ~91 |
| Phase 4 | Infrastructure | ✅ 코드 작성 완료 | ~20 | ~42 |
| Phase 5 | Application (**3개 Facade만**) | ✅ 코드 작성 완료 | 3 | 15 |
| Phase 6 | Customer API | ⬜ 미시작 | ~10 | ~42 |
| Phase 7 | Admin API | ✅ 코드 작성 완료 | ~10 | ~22 |
| Phase 8 | Batch | ✅ 코드 작성 완료 | 1 | 5 |
| Phase 9 | Integration + Refactoring | ✅ 코드 작성 완료 | ~17 | ~7 |
| **합계** | | | **~121** | **~361** |

### Phase 0 산출물 (2026-02-22)

| 유형 | 파일 | 테스트 수 |
|------|------|-----------|
| Entity | `modules/jpa/.../BaseStringIdEntity.java` | 8 |
| Enum | `support/enums/DisplayStatus.java` | 1 |
| Enum | `support/enums/ProductSaleStatus.java` | 4 |
| Enum | `support/enums/OrderStatus.java` | 4 |
| Enum | `support/enums/OrderType.java` | 1 |
| Enum | `support/enums/ProductRevisionAction.java` | - |
| Enum | `support/enums/RestoreReason.java` | - |
| Enum | `support/enums/RestoreTriggerSource.java` | - |
| Enum | `support/enums/UnavailableReason.java` | - |
| ErrorType | `support/error/ErrorType.java` (수정) | 1 |
| **합계** | 소스 10개 + 테스트 6개 = **16개 파일** | **19개 케이스** |

### Phase 1 산출물 (2026-02-22)

> Facade 제거: `UserFacade` → Controller가 `UserService` 직접 호출. `UserInfo`는 `domain/user/` 패키지로 이동.

| 레이어 | 파일 | 테스트 수 |
|--------|------|-----------|
| domain | `UserModel.java` | 19 |
| domain | `UserInfo.java` — **domain 패키지에 위치** | - |
| domain | `UserRepository.java` (interface) | - |
| domain | `PasswordEncoder.java` (interface) | - |
| domain | `UserService.java` — **Info 직접 반환** | 11 |
| infrastructure | `UserJpaRepository.java` | - |
| infrastructure | `UserRepositoryImpl.java` | 7 |
| infrastructure | `BCryptPasswordEncoder.java` (user) | - |
| interfaces | `UserV1Controller.java` — **Service 직접 호출** | - |
| interfaces | `UserV1Dto.java` | - |
| E2E | `UserV1ApiE2ETest.java` | 8 |
| **합계** | 소스 9개 + 테스트 4개 = **13개 파일** | **45개 케이스** |
