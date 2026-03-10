# 03. 도메인 모델 (Domain Model)

멘토 피드백에 따라 ERD보다 **도메인 모델링**에 집중합니다.
어떤 순수 객체(Entity, VO)가 어떤 메시지(행위)를 주고받는지 표현합니다.

---

## 1. Aggregate 구조

| Aggregate | Root Entity | 포함 요소 |
|-----------|-------------|----------|
| **Order** | `Order` | `OrderItem` (VO), `OrderStatus` (Enum) |
| **Product** | `Product` | - |
| **Option** | `Option` | - |
| **Brand** | `Brand` | - |
| **Cart** | `CartItem` | - |
| **Like** | `Like` | Hard Delete 대상 |
| **Coupon** | `Coupon` | `DiscountType` (Enum) |
| **IssuedCoupon** | `IssuedCoupon` | `IssuedCouponStatus` (Enum) |

---

## 2. 도메인 클래스 다이어그램

```mermaid
classDiagram
    direction TB

    %% ===== Order Aggregate =====
    class Order {
        -Long id
        -Long userId
        -List~OrderItem~ orderItems
        -OrderStatus status
        -Long issuedCouponId
        -Money discountAmount
        +create(userId, orderItems) Order
        +create(userId, orderItems, issuedCouponId, discountAmount) Order
        +pay() void
        +prepare() void
        +ship() void
        +deliver() void
        +cancel() void
        +validateOwner(userId) void
        +getTotalAmount() Money
        +getPaymentAmount() Money
    }

    class OrderItem {
        <<Value Object>>
        -Long optionId
        -String productName
        -String optionName
        -Money price
        -int quantity
        +of(...) OrderItem
        +getTotalPrice() Money
    }

    class OrderStatus {
        <<Enumeration>>
        PENDING
        PAID
        PREPARING
        SHIPPED
        DELIVERED
        CANCELED
        +canCancel() boolean
        +canShip() boolean
        +canDeliver() boolean
    }

    %% ===== Product & Option =====
    class Product {
        -Long id
        -Long brandId
        -String name
        -Money basePrice
        -boolean deleted
        -long likeCount
        +create(brandId, name, basePrice) Product
        +update(name, basePrice) void
        +increaseLikeCount() void
        +decreaseLikeCount() void
        +delete() void
        +restore() void
    }

    class Option {
        -Long id
        -Long productId
        -String name
        -Money additionalPrice
        -int stock
        -boolean deleted
        +create(productId, name, additionalPrice, stock) Option
        +decreaseStock(quantity) void
        +increaseStock(quantity) void
        +updateStock(newStock) void
        +isSoldOut() boolean
        +delete() void
        +restore() void
    }

    %% ===== Brand =====
    class Brand {
        -Long id
        -String name
        -boolean deleted
        +create(name) Brand
        +update(name) void
        +delete() void
        +restore() void
    }

    %% ===== Cart =====
    class CartItem {
        -Long id
        -Long userId
        -Long optionId
        -int quantity
        +create(userId, optionId, quantity) CartItem
        +of(id, userId, optionId, quantity) CartItem
        +validateOwner(userId) void
        +addQuantity(quantity) void
        +updateQuantity(quantity) void
    }

    %% ===== Like =====
    class Like {
        <<Hard Delete>>
        -Long id
        -Long userId
        -Long productId
        +create(userId, productId) Like
        +of(id, userId, productId) Like
    }

    %% ===== Coupon =====
    class Coupon {
        -Long id
        -String name
        -DiscountType discountType
        -Money discountValue
        -Money minOrderAmount
        -Money maxDiscountAmount
        -int totalQuantity
        -int issuedQuantity
        -ZonedDateTime validFrom
        -ZonedDateTime validUntil
        -boolean deleted
        +create(...) Coupon
        +issue() void
        +validateIssuable() void
        +calculateDiscount(orderAmount) Money
        +validateUsable(orderAmount) void
        +update(...) void
        +delete() void
        +restore() void
    }

    class IssuedCoupon {
        -Long id
        -Long couponId
        -Long userId
        -IssuedCouponStatus status
        -Long usedOrderId
        -ZonedDateTime issuedAt
        -ZonedDateTime expiredAt
        -ZonedDateTime usedAt
        -DiscountType discountType
        -Money discountValue
        -Money minOrderAmount
        -Money maxDiscountAmount
        +create(Coupon, userId) IssuedCoupon
        +of(...) IssuedCoupon
        +use(orderId) void
        +restore() void
        +validateUsable(orderAmount) void
        +validateOwner(userId) void
        +calculateDiscount(orderAmount) Money
        +isExpired() boolean
    }

    class DiscountType {
        <<Enumeration>>
        FIXED
        RATE
    }

    class IssuedCouponStatus {
        <<Enumeration>>
        AVAILABLE
        USED
        EXPIRED
    }

    %% ===== Value Object =====
    class Money {
        <<Value Object>>
        -BigDecimal amount
        +of(amount) Money
        +zero() Money
        +add(Money) Money
        +subtract(Money) Money
        +multiply(int) Money
        +percentage(rate) Money
        +min(Money) Money
        +isGreaterThan(Money) boolean
        +isGreaterThanOrEqual(Money) boolean
    }

    %% ===== Relationships =====
    Order "1" *-- "N" OrderItem : contains
    Order --> OrderStatus : has
    Order ..> Money : uses
    OrderItem ..> Money : uses
    Product ..> Money : uses
    Option ..> Money : uses
    Coupon ..> Money : uses
    Coupon --> DiscountType : has
    IssuedCoupon ..> Money : uses
    IssuedCoupon --> DiscountType : has
    IssuedCoupon --> IssuedCouponStatus : has

    %% ===== Logical References (ID only) =====
    Product ..> Brand : brandId
    Option ..> Product : productId
    CartItem ..> Option : optionId
    Like ..> Product : productId
    OrderItem ..> Option : optionId (참조용)
    Order ..> IssuedCoupon : issuedCouponId
    IssuedCoupon ..> Coupon : couponId
```

---

## 3. Entity 메시지 정리

### 3.1 Order (Aggregate Root)

| 메서드 | 행위 | 상태 전이 |
|--------|------|----------|
| `pay()` | 결제 처리 | PENDING → PAID |
| `prepare()` | 준비 시작 | PAID → PREPARING |
| `ship()` | 배송 시작 | PREPARING → SHIPPED |
| `deliver()` | 배송 완료 | SHIPPED → DELIVERED |
| `cancel()` | 주문 취소 | PENDING/PAID → CANCELED |
| `validateOwner(userId)` | 소유권 검증 | - (실패 시 CoreException) |
| `getTotalAmount()` | 총액 계산 | - |
| `getPaymentAmount()` | 결제 금액 계산 | - (총액 - 할인, 최소 0) |

### 3.2 Option

| 메서드 | 행위 | 비고 |
|--------|------|------|
| `decreaseStock(qty)` | 재고 차감 | 부족 시 예외 |
| `increaseStock(qty)` | 재고 증가 | 취소 시 복구 |
| `isSoldOut()` | 품절 여부 확인 | stock ≤ 0 |
| `delete()` / `restore()` | Soft Delete | - |

### 3.3 Product

| 메서드 | 행위 |
|--------|------|
| `update(...)` | 정보 수정 |
| `increaseLikeCount()` | 좋아요 수 증가 |
| `decreaseLikeCount()` | 좋아요 수 감소 (0 미만 방지) |
| `delete()` / `restore()` | Soft Delete |

### 3.4 Brand

| 메서드 | 행위 |
|--------|------|
| `update(...)` | 정보 수정 |
| `delete()` / `restore()` | Soft Delete |

### 3.5 CartItem

| 메서드 | 행위 |
|--------|------|
| `validateOwner(userId)` | 소유권 검증 (실패 시 CoreException) |
| `addQuantity(qty)` | 수량 합산 (동일 옵션 추가 시) |
| `updateQuantity(qty)` | 수량 변경 |

### 3.6 Like

행위 메서드 없음. 생성(`create`)과 삭제(Hard Delete)만 존재.

### 3.7 Coupon

| 메서드 | 행위 | 비고 |
|--------|------|------|
| `issue()` | 발급 수량 증가 | `validateIssuable()` 내부 호출 |
| `validateIssuable()` | 발급 가능 여부 검증 | 유효기간 + 잔여수량 + 삭제 여부 |
| `calculateDiscount(orderAmount)` | 할인 금액 계산 | FIXED/RATE 분기, maxDiscountAmount 상한 |
| `validateUsable(orderAmount)` | 사용 가능 여부 검증 | 유효기간 + 최소주문금액 |
| `update(...)` | 정보 수정 | totalQuantity ≥ issuedQuantity 검증 |
| `delete()` / `restore()` | Soft Delete | - |

### 3.8 IssuedCoupon

| 메서드 | 행위 | 상태 전이 |
|--------|------|----------|
| `create(Coupon, userId)` | 스냅샷 생성 | → AVAILABLE |
| `use(orderId)` | 쿠폰 사용 | AVAILABLE → USED |
| `restore()` | 사용 취소 (주문 취소 시) | USED → AVAILABLE |
| `validateUsable(orderAmount)` | 사용 가능 여부 검증 | 만료·최소주문금액 확인 |
| `validateOwner(userId)` | 소유권 검증 | 실패 시 CoreException |
| `calculateDiscount(orderAmount)` | 할인 금액 계산 | 스냅샷 데이터 기반 |
| `isExpired()` | 만료 여부 확인 | - |

---

## 4. Value Object 정리

### 4.1 Money

| 메서드 | 설명 |
|--------|------|
| `of(amount)` | 생성 (0 이상 검증) |
| `zero()` | 0원 생성 |
| `add(Money)` | 덧셈 (새 객체 반환) |
| `subtract(Money)` | 뺄셈 (결과 < 0 시 예외) |
| `multiply(int)` | 곱셈 (새 객체 반환) |
| `percentage(rate)` | 비율 계산 (0~100, 내림 처리) |
| `min(Money)` | 두 값 중 작은 값 반환 |
| `isGreaterThan(Money)` | 초과 비교 |
| `isGreaterThanOrEqual(Money)` | 이상 비교 |

### 4.2 OrderItem

| 특징 | 설명 |
|------|------|
| 스냅샷 | 주문 시점의 productName, optionName, price 저장 |
| 불변 | 생성 후 변경 불가 |
| `getTotalPrice()` | price × quantity |

---

## 5. 설계 원칙 요약

| 원칙 | 적용 |
|------|------|
| **Rich Domain Model** | Entity가 JPA `@Entity`를 직접 보유하면서 비즈니스 규칙도 포함 |
| **Setter 금지** | 도메인 메서드로만 상태 변경 |
| **자가 검증** | 생성자에서 CoreException 발생 |
| **논리적 FK** | ID로만 참조, 물리적 제약 없음 |
| **스냅샷 아키텍처** | IssuedCoupon은 발급 시점에 Coupon의 할인 조건을 복사 |
