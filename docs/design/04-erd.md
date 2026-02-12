# Entity Relationship Diagram

```mermaid
erDiagram
    brand {
        bigint id PK
        varchar name UK
        varchar description
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }
    product {
        bigint id PK
        bigint brand_id
        varchar name
        varchar description
        int price
        int stock_quantity
        datetime created_at
        datetime updated_at
        datetime deleted_at
    }
    likes {
        bigint id PK
        bigint user_id
        bigint product_id
        datetime created_at
    }
    orders {
        bigint id PK
        bigint user_id
        int total_amount
        datetime created_at
    }
    order_item {
        bigint id PK
        bigint order_id
        bigint product_id
        varchar product_name
        varchar brand_name
        int unit_price
        int quantity
        datetime created_at
    }
    brand ||--o{ product : ""
    product ||--o{ likes : ""
    orders ||--|{ order_item : ""
    order_item }o--|| product : ""
```

### 연관 관계

> 모든 관계는 논리적 연관이며, 물리적 FK 제약조건은 적용하지 않는다.
> 참조 무결성은 애플리케이션 레벨에서 보장한다.

### 삭제 정책

| 테이블 | 삭제 방식 | deleted_at | 이유 |
| --- | --- | --- | --- |
| brand | Soft Delete | O | 이력 보존, 하위 상품 연쇄 삭제 |
| product | Soft Delete | O | 주문 스냅샷 참조 안전성 |
| likes | Hard Delete | X | 이력 보존 가치 없음 |
| orders | 삭제 불가 | X | 정산, CS, 법적 보관 의무 |
| order_item | 삭제 불가 | X | 스냅샷 불변 |

### 제약 조건

| 테이블 | 제약 | 컬럼 | 설명 |
| --- | --- | --- | --- |
| likes | UNIQUE | user_id, product_id | 1인 1좋아요 보장 |