# 향후 확장 클래스 구조

> 현재 구현 범위 이후 도입할 도메인의 클래스 구조를 정리한다.

---

## A. 재고 예약 모델 (Inventory)

현재 `Product.stock`으로 즉시 차감하지만, 결제 도메인 도입 시 예약 모델로 확장한다.

```mermaid
classDiagram
    class Inventory {
        -Long id
        -Long productId
        -int quantity
        -int reservedQty
        -int safetyStock
        +getSellable() int
        +reserve(qty) void
        +confirmReservation(qty) void
        +releaseReservation(qty) void
    }

    Product "1" --> "1" Inventory : productId
```

- **현재**: `Product.stock`으로 즉시 차감 (현재 구현 범위)
- **확장 시**: `Inventory`로 분리, `getSellable() = quantity - reservedQty`
- **Variant 도입 시**: `productId` → `variantId`로 전환, `Product 1:N Variant 1:1 Inventory`

---

## B. 쿠폰 시스템 (Coupon)

```mermaid
classDiagram
    class CouponTemplate {
        -Long id
        -String name
        -DiscountType discountType
        -int discountValue
        -int maxDiscountAmount
        -int minOrderAmount
        -LocalDateTime validFrom
        -LocalDateTime validUntil
        -CouponStatus status
        +isValid() boolean
        +calculateDiscount(orderAmount) int
    }

    class IssuedCoupon {
        -Long id
        -Long userId
        -Long couponTemplateId
        -String code
        -IssuedCouponStatus status
        -Long redeemedOrderId
        +reserve() void
        +redeem(orderId) void
        +release() void
        +expire() void
        +isUsable() boolean
    }

    class CouponTarget {
        -Long id
        -Long couponTemplateId
        -CouponTargetType targetType
        -Long targetId
    }

    class DiscountType {
        <<enumeration>>
        FIXED
        PERCENT
    }

    class IssuedCouponStatus {
        <<enumeration>>
        ISSUED
        RESERVED
        REDEEMED
        EXPIRED
        CANCELED
    }

    class CouponTargetType {
        <<enumeration>>
        ALL
        PRODUCT
        BRAND
    }

    CouponTemplate "1" --> "*" IssuedCoupon : couponTemplateId
    CouponTemplate "1" --> "*" CouponTarget : couponTemplateId
```

---

## C. 결제/옵션/장바구니 (방향만 제시)

```mermaid
classDiagram
    class Variant {
        -Long id
        -Long productId
        -String skuCode
        -int extraPrice
        -VariantStatus status
        +getUnitPrice(basePrice) int
    }

    class OptionGroup {
        -Long id
        -Long productId
        -String code
        -String name
    }

    class OptionValue {
        -Long id
        -Long optionGroupId
        -String value
    }

    class OrderSheet {
        -Long id
        -Long userId
        -OrderSheetStatus status
        -LocalDateTime expiresAt
        +assertDraft() void
        +transitionToValidating() void
        +transitionToReady() void
        +isExpired() boolean
    }

    class Payment {
        -Long id
        -Long orderId
        -PaymentStatus status
        -int requestedAmount
        -String pgTxnId
        +authorize() void
        +markFailed(reason) void
        +isTerminal() boolean
    }

    Product "1" --> "*" Variant
    Product "1" --> "*" OptionGroup
    OptionGroup "1" --> "*" OptionValue
    Variant --> OptionValue : 조합
    Order --> OrderSheet : 기반
    Payment --> Order
```
