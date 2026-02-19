### 4.4 ERD (Entity Relationship Diagram)
### 제약 최소화(복합 PK 중심) 
**왜 이 다이어그램이 필요한가:**
영속성 구조, 관계의 주인, 인덱스 전략을 검증하기 위해 필요하다. 특히 **재고 테이블의 분리**, **주문 만료 인덱스**, **좋아요/장바구니의 복합 PK(중복 방지)** 등 DB 설계의 핵심 결정을 확인한다.

```mermaid

erDiagram
    users {
        bigint user_id PK
        varchar login_id
        varchar password "bcrypt"
        varchar user_name
        varchar email
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    brands {
        bigint brand_id PK
        varchar brand_seq
        varchar brand_name
        varchar description
        varchar status "ACTIVE/HIDDEN/DELETED"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    products {
        bigint product_id PK
        bigint product_seq PK
        bigint brand_id
        varchar name
        text description
        decimal price
        varchar category
        varchar color
        varchar size
        varchar option
        varchar image_url
        varchar status "ACTIVE/HIDDEN/DELETED"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    product_stocks {
        bigint product_id PK
        int on_hand "총 재고"
        int reserved "예약 재고"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    product_revisions {
        bigint product_id PK
        bigint revision_seq PK
        varchar changed_by "Admin ID"
        varchar change_reason "nullable"
        json snapshot "현재 상태"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    likes {
        bigint user_id PK
        bigint product_id PK
        datetime created_at
    }

    cart_items {
        bigint user_id PK
        bigint product_id PK
        int quantity
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    orders {
        bigint order_id PK
        bigint user_id PK
        varchar order_type "DIRECT/CART"
        varchar status "PENDING_PAYMENT/PAID/PAYMENT_FAILED/CANCELLED/EXPIRED"
        decimal total_amount
        datetime expires_at
        datetime paid_at "nullable(Phase2)"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    order_items {
        bigint order_id PK
        bigint user_id PK
        bigint order_item_seq PK
        bigint product_id
        int quantity
        varchar snapshot_product_name
        decimal snapshot_unit_price
        bigint snapshot_brand_id
        varchar snapshot_brand_name
        varchar snapshot_image_url
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    order_cart_restores {
        bigint order_id PK
        bigint user_id PK
        varchar reason "EXPIRED/CANCELLED/PAYMENT_FAILED"
        datetime restored_at
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    users ||--o{ likes : "places"
    users ||--o{ cart_items : "owns"
    users ||--o{ orders : "places"
    brands ||--o{ products : "has"
    products ||--|| product_stocks : "has"
    products ||--o{ product_revisions : "tracks"
    products ||--o{ likes : "receives"
    products ||--o{ cart_items : "referenced_by"
    products ||--o{ order_items : "snapshot_of"
    orders ||--o{ order_items : "contains"
    orders ||--o| order_cart_restores : "may_restore"
```
