# 02. 시퀀스 다이어그램

---

## 작성 원칙

- 기능 하나당 하나의 시퀀스
- 최소 레이어: User → Controller → Service/Facade → Domain(Entity/Policy) → Repository
- 존재 여부 체크, 분기(alt/else), 상태 변경을 표현
- 도메인 객체가 "빈 껍데기"가 되지 않도록, 검증/상태 전이 책임을 도메인에 부여
- 01-requirements.md에 정의된 API URI 기준으로 작성

## 시퀀스 선별 기준

단순 CRUD는 생략하고, **상태 전이/동시성 제어/연쇄 처리/다중 도메인 조율** 등 설계 의도가 드러나는 핵심 흐름만 선별.

| # | 시퀀스 | 선별 이유 |
|---|--------|----------|
| 1 | 주문 생성 (재고 예약) | 핵심 흐름: 재고 예약 + 스냅샷 + 만료 설정 |
| 2 | 결제 요청 (성공/실패) | 핵심 흐름: 6개 도메인 조율 (가장 복잡한 트랜잭션) |
| 3 | 예약 만료 배치 | 배치 처리: 만료된 예약/주문 자동 정리 |
| 4 | 상품 좋아요 등록/취소 | hard delete + like_count 동기 증감 |
| 5 | 브랜드 삭제 (연쇄) | Aggregate 간 연쇄 soft delete |
| 6 | 장바구니 → 주문 전환 | 장바구니 기반 주문 생성 흐름 |
| 7 | 쿠폰 발급 → 적용 | 쿠폰 라이프사이클 |
| 8 | 어드민 상품 등록 | 브랜드 검증 + 재고 초기화 |

---

## 1) 주문 생성 (재고 예약 + 스냅샷)

핵심: 복수 상품 → 재고 예약(HELD) → Order(PENDING) + OrderItem(스냅샷) → 30분 만료 설정

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as OrderController
  participant Service as OrderService
  participant AddrRepo as UserAddressRepository
  participant ProductRepo as ProductRepository
  participant InvSvc as InventoryService
  participant InvRepo as InventoryRepository
  participant Order as Order(Entity)
  participant OrderRepo as OrderRepository

  User->>Controller: POST /api/v1/orders {items, addressId}
  Note over Controller: X-Loopers-LoginId/LoginPw 헤더로 사용자 식별
  Controller->>Service: createOrder(userId, items, addressId)

  Service->>Service: validateItems(items)
  alt items 비어있음 또는 수량 비정상
    Service-->>Controller: 400 주문 항목이 비어있습니다
    Controller-->>User: 400
  end

  Service->>AddrRepo: findById(addressId)
  alt 주소 없음
    AddrRepo-->>Service: empty
    Service-->>Controller: 404 존재하지 않는 배송지
    Controller-->>User: 404
  end

  loop 각 item에 대해
    Service->>ProductRepo: findById(item.productId)
    alt 상품 없음 또는 판매 불가
      Service-->>Controller: 404/409 판매 불가 상품 포함
      Controller-->>User: error
    end
  end

  Note over Service,InvSvc: 트랜잭션 시작 - 재고 예약은 원자적으로 수행
  Service->>InvSvc: reserve(userId, items, expiresAt=now+30min)

  loop 각 item에 대해 (비관적 락)
    InvSvc->>InvRepo: findByProductIdForUpdate(productId)
    Note over InvSvc,InvRepo: SELECT ... FOR UPDATE (행 잠금)
    InvSvc->>InvSvc: 가용 확인: quantity - reserved_qty >= 요청수량
    alt 재고 부족
      Note over InvSvc: 트랜잭션 롤백 - 이전 예약 모두 취소
      InvSvc-->>Service: 409 재고 부족
      Service-->>Controller: 409
      Controller-->>User: 409 재고 부족
    else 재고 충분
      InvSvc->>InvSvc: inventories.reserved_qty += quantity
      InvSvc->>InvRepo: save(inventory)
    end
  end

  Note over InvSvc: InventoryReservation(HELD) + Items 생성
  InvSvc-->>Service: reservationId

  Note over Service,Order: 스냅샷 생성 - 주문 시점 상품 정보 고정
  Service->>Order: create(userId, products, items, address, reservationId)
  Note over Order: Order(PENDING) 생성, expires_at = now + 30분
  Note over Order: OrderItem 생성 (상품명, 브랜드명, 가격, 수량 스냅샷)
  Note over Order: 배송지 스냅샷 저장

  Service->>OrderRepo: save(order)
  Service-->>Controller: 200 {orderId, orderNumber, status: PENDING, expiresAt}
  Controller-->>User: 200 OK
```

**책임 분리 포인트**:
- `OrderService`: 흐름 조율 (애플리케이션 서비스)
- `InventoryService`: 재고 예약 검증/수행 (도메인 서비스)
- `Order(Entity)`: 주문 생성 + OrderItem 스냅샷 조립 (도메인)
- `InventoryRepository`: 비관적 락 + 영속화 (인프라)

**트랜잭션 경계**: `createOrder` 전체가 하나의 `@Transactional`

---

## 2) 결제 요청 (성공/실패 분기)

핵심: Payment 생성 → PG 승인 → 성공 시 6개 도메인 확정 / 실패 시 보상 처리

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as PaymentController
  participant PaymentSvc as PaymentService
  participant OrderSvc as OrderService
  participant InvSvc as InventoryService
  participant PointSvc as PointService
  participant CouponSvc as CouponService
  participant PG as PG(외부)

  User->>Controller: POST /api/v1/orders/{orderId}/pay {paymentMethod}
  Controller->>PaymentSvc: requestPayment(userId, orderId, paymentMethod)

  PaymentSvc->>OrderSvc: getOrder(orderId)
  alt 주문 없음 또는 본인 아님
    OrderSvc-->>PaymentSvc: 404/403
    PaymentSvc-->>Controller: error
    Controller-->>User: error
  end
  alt 주문 PENDING이 아님 (이미 결제/만료/취소)
    OrderSvc-->>PaymentSvc: 409 결제 불가 상태
    PaymentSvc-->>Controller: 409
    Controller-->>User: 409
  end

  Note over PaymentSvc: Payment(REQUESTED) 생성 + idempotencyKey
  PaymentSvc->>PG: 결제 승인 요청 (amount, idempotencyKey)
  PG-->>PaymentSvc: 승인 결과

  alt 결제 성공
    Note over PaymentSvc: Payment → APPROVED, approvedAmount 기록

    PaymentSvc->>InvSvc: commit(reservationId)
    Note over InvSvc: Reservation → COMMITTED
    Note over InvSvc: inventories.quantity -= 수량
    Note over InvSvc: inventories.reserved_qty -= 수량

    PaymentSvc->>PointSvc: use(userId, pointAmount, orderId)
    Note over PointSvc: point_accounts.balance -= pointAmount
    Note over PointSvc: point_ledgers 기록 (USE)

    PaymentSvc->>CouponSvc: redeem(couponCodes, orderId)
    Note over CouponSvc: issued_coupons → REDEEMED
    Note over CouponSvc: redeemed_order_id = orderId

    PaymentSvc->>OrderSvc: confirmOrder(orderId, paymentId)
    Note over OrderSvc: Order → PAID
    Note over OrderSvc: payment_id, ordered_at 설정

    PaymentSvc-->>Controller: 200 결제 완료
    Controller-->>User: 200 {orderNumber, status: PAID}

  else 결제 실패
    Note over PaymentSvc: Payment → FAILED

    PaymentSvc->>InvSvc: release(reservationId)
    Note over InvSvc: Reservation → RELEASED
    Note over InvSvc: inventories.reserved_qty -= 수량

    PaymentSvc->>CouponSvc: release(couponCodes)
    Note over CouponSvc: issued_coupons → ISSUED (복구)

    Note over PaymentSvc: 포인트는 결제 성공 시에만 차감하므로 복구 불필요

    PaymentSvc-->>Controller: 결제 실패 (재시도 가능)
    Controller-->>User: 409 결제 실패
  end
```

**설계 의도**:
- 결제 성공 시 6개 도메인이 하나의 트랜잭션에서 확정
- 포인트는 결제 성공 후에만 차감 → 실패 시 포인트 복구 불필요
- 쿠폰은 주문 생성 시 RESERVED → 결제 실패 시 ISSUED로 복구

---

## 3) 예약 만료 배치 처리

핵심: 30분 경과 후 미결제 주문/예약 자동 정리

```mermaid
sequenceDiagram
  autonumber
  participant Batch as BatchScheduler
  participant ResvRepo as ReservationRepository
  participant InvRepo as InventoryRepository
  participant OrderRepo as OrderRepository
  participant CouponRepo as IssuedCouponRepository

  Note over Batch: 주기적 실행 (예: 1분마다)
  Batch->>ResvRepo: findByStatusAndExpiresAtBefore(HELD, now())
  ResvRepo-->>Batch: expiredReservations

  loop 각 만료된 예약에 대해
    Note over Batch: 트랜잭션 시작

    Batch->>ResvRepo: 예약 상태 → EXPIRED

    loop 각 예약 항목에 대해
      Batch->>InvRepo: findByProductIdForUpdate(productId)
      Batch->>InvRepo: inventories.reserved_qty -= quantity
    end

    Batch->>OrderRepo: findByReservationId(reservationId)
    Batch->>OrderRepo: order.status → EXPIRED

    Batch->>CouponRepo: findReservedByOrderId(orderId)
    loop RESERVED 상태 쿠폰
      Batch->>CouponRepo: issued_coupons.status → ISSUED (복구)
    end

    Note over Batch: 트랜잭션 커밋
  end
```

**배치 설계 포인트**:
- `commerce-batch` 모듈에서 Spring Batch 또는 `@Scheduled`로 구현
- 각 만료 예약 처리는 개별 트랜잭션 (하나 실패해도 나머지 영향 없음)
- 만료 시각 기준으로 인덱스 활용: `idx_reservations_status_expires_at`

---

## 4) 상품 좋아요 등록 / 취소

### 등록

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as LikeController
  participant Service as LikeService
  participant ProductRepo as ProductRepository
  participant LikeRepo as ProductLikeRepository
  participant Product as Product(Entity)

  User->>Controller: POST /api/v1/products/{productId}/likes
  Controller->>Service: createLike(userId, productId)

  Service->>ProductRepo: findById(productId)
  alt 상품 없음 또는 노출 불가
    Service-->>Controller: 404 존재하지 않는 상품
    Controller-->>User: 404
  end

  Service->>LikeRepo: findByUserIdAndProductId(userId, productId)
  alt 이미 좋아요 존재
    Service-->>Controller: 409 이미 좋아요한 상품
    Controller-->>User: 409
  end

  Note over Service,LikeRepo: DB UNIQUE(user_id, product_id) 제약으로 동시성 방어
  Service->>LikeRepo: save(ProductLike(userId, productId))

  Service->>Product: incrementLikeCount()
  Service->>ProductRepo: save(product)

  Service-->>Controller: 200 {liked: true, likeCount: N}
  Controller-->>User: 200 OK
```

### 취소 (hard delete)

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as LikeController
  participant Service as LikeService
  participant LikeRepo as ProductLikeRepository
  participant Product as Product(Entity)
  participant ProductRepo as ProductRepository

  User->>Controller: DELETE /api/v1/products/{productId}/likes
  Controller->>Service: cancelLike(userId, productId)

  Service->>LikeRepo: findByUserIdAndProductId(userId, productId)
  alt 좋아요 없음
    Service-->>Controller: 404 좋아요한 기록이 없습니다
    Controller-->>User: 404
  end

  Service->>LikeRepo: delete(like)
  Note over Service,LikeRepo: hard delete (row 삭제)

  Service->>ProductRepo: findById(productId)
  Service->>Product: decrementLikeCount()
  Service->>ProductRepo: save(product)

  Service-->>Controller: 200 {liked: false, likeCount: N}
  Controller-->>User: 200 OK
```

**hard delete 설계 근거**:
- 이력 추적 불필요
- `(user_id, product_id)` UK와 soft delete 조합 시 복잡도 증가 회피
- like_count 증감은 동일 트랜잭션 내에서 보장

---

## 5) 브랜드 삭제 (연쇄 상품 + 재고 삭제)

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant Controller as BrandAdminController
  participant Service as BrandAdminService
  participant BrandRepo as BrandRepository
  participant ProductRepo as ProductRepository
  participant InvRepo as InventoryRepository
  participant Brand as Brand(Entity)

  Admin->>Controller: DELETE /api-admin/v1/brands/{brandId}
  Note over Controller: X-Loopers-Ldap 헤더 검증
  Controller->>Service: deleteBrand(brandId)

  Service->>BrandRepo: findById(brandId)
  alt 브랜드 없음
    Service-->>Controller: 404 존재하지 않는 브랜드
    Controller-->>Admin: 404
  end

  Service->>Brand: assertNotDeleted()
  alt 이미 삭제됨
    Service-->>Controller: 409 이미 삭제된 브랜드
    Controller-->>Admin: 409
  end

  Note over Service,Brand: 브랜드 소프트 삭제
  Service->>Brand: delete()
  Note over Brand: deleted_at = now()
  Service->>BrandRepo: save(brand)

  Note over Service,ProductRepo: 소속 상품 일괄 소프트 삭제
  Service->>ProductRepo: softDeleteAllByBrandId(brandId)
  Note over ProductRepo: UPDATE products SET deleted_at=now() WHERE brand_id=? AND deleted_at IS NULL

  Note over Service,InvRepo: 소속 재고 일괄 소프트 삭제
  Service->>InvRepo: softDeleteAllByProductIds(productIds)
  Note over InvRepo: UPDATE inventories SET deleted_at=now() WHERE product_id IN(?) AND deleted_at IS NULL

  Service-->>Controller: 200 {brandId, deletedProductCount}
  Controller-->>Admin: 200 OK
```

**설계 의도**:
- 브랜드 + 상품 + 재고 삭제는 하나의 트랜잭션 (정합성 보장)
- 기존 주문의 OrderItem 스냅샷에는 영향 없음

---

## 6) 장바구니 → 주문 전환

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as OrderController
  participant Service as OrderService
  participant CartRepo as CartRepository
  participant CartItemRepo as CartItemRepository

  User->>Controller: POST /api/v1/orders {items: [...], addressId: 5}
  Note over Controller: items는 장바구니에서 선택한 항목

  Controller->>Service: createOrder(userId, items, addressId)
  Note over Service: 이후 흐름은 시퀀스 1)과 동일<br>(재고 예약 → Order 생성 → 스냅샷)

  Note over Service: 주문 생성 성공 후
  Service->>CartItemRepo: softDeleteByCartIdAndProductIds(cartId, productIds)
  Note over CartItemRepo: 주문된 상품을 장바구니에서 제거

  Service-->>Controller: 200 {orderId, orderNumber}
  Controller-->>User: 200 OK
```

**설계 포인트**:
- 장바구니 → 주문은 별도 API가 아닌, 주문 생성 API의 입력으로 처리
- 주문 생성 성공 후 해당 항목을 장바구니에서 제거 (soft delete)

---

## 7) 쿠폰 발급 → 적용 → 사용 확정

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as CouponController
  participant CouponSvc as CouponService
  participant TemplateRepo as CouponTemplateRepository
  participant IssuedRepo as IssuedCouponRepository
  participant TargetRepo as CouponTargetRepository

  Note over User, TargetRepo: Phase 1: 쿠폰 발급

  User->>Controller: POST /api/v1/coupons/issue {couponTemplateId}
  Controller->>CouponSvc: issueCoupon(userId, templateId)

  CouponSvc->>TemplateRepo: findById(templateId)
  alt 템플릿 없음 또는 비활성
    CouponSvc-->>Controller: 404
  end

  CouponSvc->>CouponSvc: 발급 제한 확인 (전체/유저별)
  alt 발급 제한 초과
    CouponSvc-->>Controller: 409 발급 제한 초과
  end

  CouponSvc->>CouponSvc: 고유 코드 생성
  CouponSvc->>IssuedRepo: save(IssuedCoupon(ISSUED))
  CouponSvc-->>Controller: 200 {couponCode, status: ISSUED}
  Controller-->>User: 200 OK

  Note over User, TargetRepo: Phase 2: 주문에 쿠폰 적용

  User->>Controller: PUT /api/v1/orders/{orderId}/discount {couponCodes: [...]}
  Controller->>CouponSvc: reserveCoupons(userId, orderId, couponCodes)

  loop 각 쿠폰에 대해
    CouponSvc->>IssuedRepo: findByCodeAndUserId(code, userId)
    alt 쿠폰 없음 또는 본인 아님
      CouponSvc-->>Controller: 404
    end
    alt ISSUED가 아님 (이미 사용/만료)
      CouponSvc-->>Controller: 409
    end

    CouponSvc->>TargetRepo: findByCouponTemplateId(templateId)
    CouponSvc->>CouponSvc: 대상 상품/브랜드 매칭 확인
    CouponSvc->>CouponSvc: 최소 주문금액 확인

    CouponSvc->>IssuedRepo: status → RESERVED
  end

  CouponSvc->>CouponSvc: 할인 금액 계산
  CouponSvc-->>Controller: discountAmount
```

**쿠폰 상태 전이 요약**:
- `ISSUED` → 주문 적용 → `RESERVED`
- `RESERVED` → 결제 성공 → `REDEEMED` (사용 확정, 취소 불가)
- `RESERVED` → 결제 실패/만료 → `ISSUED` (복구)
- `ISSUED` → 유효기간 만료 → `EXPIRED`

---

## 8) 어드민 상품 등록 (브랜드 검증 + 재고 초기화)

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant Controller as ProductAdminController
  participant Service as ProductAdminService
  participant BrandRepo as BrandRepository
  participant Product as Product(Entity)
  participant ProductRepo as ProductRepository
  participant InvRepo as InventoryRepository

  Admin->>Controller: POST /api-admin/v1/products {brandId, name, basePrice, initialStock, ...}
  Note over Controller: X-Loopers-Ldap 헤더 검증
  Controller->>Service: createProduct(request)

  Service->>BrandRepo: findById(request.brandId)
  alt 브랜드 없음
    Service-->>Controller: 404 존재하지 않는 브랜드
    Controller-->>Admin: 404
  end

  Service->>Service: assertBrandActive(brand)
  alt 브랜드 비활성/삭제
    Service-->>Controller: 409 삭제된 브랜드에 상품 등록 불가
    Controller-->>Admin: 409
  end

  Service->>Product: create(brandId, name, basePrice, ...)
  Note over Product: 초기 status=ACTIVE
  Service->>ProductRepo: save(product)

  Note over Service,InvRepo: 재고 레코드 함께 생성
  Service->>InvRepo: save(Inventory(productId, quantity=initialStock))
  Note over InvRepo: inventories 레코드 생성 (reserved_qty=0)

  Service-->>Controller: 201 {productId, ...}
  Controller-->>Admin: 201 Created
```

**설계 의도**:
- 상품 등록 시 inventories 레코드도 함께 생성 (1:1 관계 보장)
- 브랜드가 ACTIVE일 때만 상품 등록 가능