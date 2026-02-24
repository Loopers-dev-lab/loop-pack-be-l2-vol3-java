# 04. ERD (Entity Relationship Diagram)

---

## 1. 전체 ERD

> 모든 관계선은 **논리적 연결**이며, 물리적 FK 제약조건은 적용하지 않음

```mermaid
erDiagram
    BRAND ||--o{ PRODUCT : "has"
    PRODUCT ||--o{ PRODUCT_OPTION : "has"
    MEMBER ||--o{ LIKES : "likes"
    PRODUCT ||--o{ LIKES : "liked_by"
    MEMBER ||--o{ CART_ITEM : "has"
    PRODUCT_OPTION ||--o{ CART_ITEM : "in_cart"
    MEMBER ||--o{ ORDERS : "places"
    ORDERS ||--o{ ORDER_ITEM : "contains"
    PRODUCT_OPTION ||--o{ ORDER_ITEM : "ordered"

    BRAND {
        bigint id PK
        varchar name UK
    }

    PRODUCT {
        bigint id PK
        bigint brand_id
        varchar name
        bigint base_price
    }

    PRODUCT_OPTION {
        bigint id PK
        bigint product_id
        varchar name
        bigint additional_price
        int stock
    }

    MEMBER {
        bigint id PK
        varchar member_id UK
    }

    LIKES {
        bigint id PK
        bigint user_id
        bigint product_id
    }

    CART_ITEM {
        bigint id PK
        bigint user_id
        bigint option_id
        int quantity
    }

    ORDERS {
        bigint id PK
        bigint user_id
        varchar status
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id
        bigint option_id
        varchar product_name "스냅샷"
        varchar option_name "스냅샷"
        bigint price "스냅샷"
        int quantity
    }
```

---

## 2. 삭제 정책

| 테이블 | 삭제 방식 | 이유 |
|--------|----------|------|
| BRAND | Soft Delete | 이력 보존, Product와 논리적 연결 유지 |
| PRODUCT | Soft Delete | 주문 내역에서 참조 가능성 |
| PRODUCT_OPTION | Soft Delete | 주문 내역에서 참조 가능성 |
| **LIKES** | **Hard Delete** | 이력 보존 가치 낮음, 토글 로직 단순화 |
| CART_ITEM | Hard Delete | 임시 데이터 |
| ORDERS | 삭제 불가 | 거래 이력 필수 보존 |
| ORDER_ITEM | 삭제 불가 | 거래 이력 필수 보존 |

---

## 3. 스냅샷 정책

**ORDER_ITEM**은 주문 시점의 상품명, 옵션명, 가격을 복사 저장한다.
원본(Product, Option)이 변경/삭제되어도 주문 내역은 그대로 유지된다.
