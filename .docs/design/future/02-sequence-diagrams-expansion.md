# 향후 확장 시퀀스 다이어그램: 옵션/Variant 시스템

> Variant 도입 시 변경되는 핵심 흐름을 시퀀스로 정리한다.
> 현재 설계에서 Product 단위로 동작하는 흐름이 Variant 단위로 확장된다.

---

## A. Variant 단위 주문 생성 흐름

현재 설계에서는 `productId + quantity`로 주문을 생성하지만, Variant 도입 시 `variantId + quantity`로 전환된다. 재고 예약도 Variant 단위로 변경된다.

```mermaid
sequenceDiagram
  autonumber
  actor User
  participant Controller as OrderController
  participant Service as OrderService
  participant Product as Product(Entity)
  participant Variant as Variant(Entity)
  participant Inventory as Inventory(Entity)
  participant InvRepo as InventoryRepository

  User->>Controller: POST /orders (variantId, quantity, ...)

  Service->>Variant: findById(variantId)
  Variant-->>Service: variant (productId, extraPrice, skuCode)

  Service->>Product: findById(variant.productId)
  Product-->>Service: product (basePrice)

  Note over Service: unitPrice = basePrice + extraPrice

  Service->>InvRepo: findByVariantIdForUpdate(variantId)
  InvRepo-->>Service: inventory (Variant 단위 재고)
  Service->>Inventory: reserve(quantity)
  Note over Inventory: reservedQty += quantity

  Note over Service: OrderItem 생성 시<br/>variantId, 옵션 스냅샷(컬러: 빨강, 사이즈: M) 포함

  Service-->>User: Order(PENDING, expiresAt)
```

**현재 설계와의 차이점**:
- `productId` → `variantId`로 재고/주문 항목 단위 변경
- 단가 계산: `basePrice` 단일 → `basePrice + extraPrice`
- OrderItem에 옵션 정보 스냅샷 추가 필요

---

## B. 상품 옵션 설정 흐름 (Admin)

```mermaid
sequenceDiagram
  autonumber
  actor Admin
  participant Controller as ProductAdminController
  participant Service as ProductAdminService
  participant Product as Product(Entity)
  participant OptGroup as OptionGroup(Entity)
  participant OptValue as OptionValue(Entity)
  participant Variant as Variant(Entity)
  participant Inventory as Inventory(Entity)

  Admin->>Controller: POST /admin/products/{id}/option-groups
  Service->>Product: findById(productId)
  Service->>OptGroup: create(productId, "컬러")
  Service->>OptValue: create(optionGroupId, "빨강")
  Service->>OptValue: create(optionGroupId, "파랑")

  Admin->>Controller: POST /admin/products/{id}/option-groups
  Service->>OptGroup: create(productId, "사이즈")
  Service->>OptValue: create(optionGroupId, "S")
  Service->>OptValue: create(optionGroupId, "M")

  Admin->>Controller: POST /admin/products/{id}/variants/generate
  Note over Service: 조합 생성: 빨강-S, 빨강-M, 파랑-S, 파랑-M

  loop 각 조합에 대해
    Service->>Variant: create(productId, skuCode, extraPrice)
    Service->>Inventory: create(variantId, quantity=0)
  end

  Service-->>Admin: 4개 Variant 생성 완료
```

**핵심 포인트**:
- OptionGroup/OptionValue 생성 → Variant 조합 생성 → Inventory 자동 초기화
- Variant 생성 시 Inventory도 함께 생성 (현재 상품 생성 시 Inventory 초기화와 동일 패턴)