### 4.4 ERD (Entity Relationship Diagram)
### 제약 최소화(복합 PK 중심) 
**왜 이 다이어그램이 필요한가:**
영속성 구조, 관계의 주인, 인덱스 전략을 검증하기 위해 필요하다. 특히 **재고 테이블의 분리**, **주문 만료 인덱스**, **좋아요/장바구니의 복합 PK(중복 방지)** 등 DB 설계의 핵심 결정을 확인한다.

```mermaid

erDiagram
    users { 
        varchar user_id PK
        varchar password "bcrypt"
        varchar user_name
        varchar birthday
        varchar email
        varchar address
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    brands {
        varchar brand_id PK
        text brand_name
        varchar description
        varchar address
        varchar display_status "ACTIVE/HIDDEN"
        varchar attach_file
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }

    products {
        varchar product_id PK
        varchar revision_seq
        varchar brand_id
        varchar product_name
        text description
        decimal price
        varchar category
        varchar color
        varchar size
        varchar option
        varchar image_url
        varchar attach_file
        varchar display_status "ACTIVE/HIDDEN"
        varchar sale_status "ON_SALE / TEMP_SOLD_OUT / STOPPED"
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }
    
    product_revisions {
        varchar   product_id PK
        bigint    revision_seq PK  
        varchar   action      "UPDATE|HIDE|DELETE|RESTORE|SALE_STATUS_CHANGE"
        varchar   changed_by  "admin_user_id or system"
        varchar   change_reason
        json      before_snapshot
        json      after_snapshot
        datetime created_at
    }
        
    product_stocks {
        varchar product_id PK
        int on_hand "총 재고"
        int reserved "예약 재고"
        datetime created_at
        datetime updated_at
    }

    likes {
        varchar user_id PK
        varchar product_id PK
        datetime created_at
    }

    cart_items {
        varchar user_id PK
        varchar product_id PK
        int quantity
        datetime created_at
        timestamp updated_at
    }

    orders {
        varchar order_id PK
        varchar user_id 
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
        varchar order_id PK
        int order_item_seq PK
        varchar user_id
        varchar product_id
        int quantity
        varchar snapshot_product_name
        decimal snapshot_unit_price
        varchar snapshot_brand_id
        varchar snapshot_brand_name
        varchar snapshot_image_url
        varchar del_yn "Y,N"
        datetime deleted_at "nullable"
        datetime created_at
        datetime updated_at
    }
    
    
    order_cart_restore {
        varchar   order_id PK          "멱등키(주문당 1회만 복원)"
        varchar   user_id              "복원 대상 사용자"
        varchar   reason               "PAYMENT_FAILED|EXPIRED|USER_CANCELLED|PG_CANCELLED"
        varchar   trigger_source       "CANCEL_API|PG_WEBHOOK|EXPIRE_JOB|MANUAL"
        timestamp restored_at          "복원 처리 완료 시각"
    }

    users ||--o{ likes : "places"
    users ||--o{ cart_items : "owns"
    users ||--o{ orders : "places"
    brands ||--o{ products : "has"
    products ||--|| product_stocks : "has"
    products ||--o{ likes : "receives"
    products ||--o{ cart_items : "referenced_by"
    products ||--o{ order_items : "snapshot_of"
    orders ||--o{ order_items : "contains"
    orders ||--o| order_cart_restore : "restored once"
    cart_items }o--|| orders : "same user"
    products ||--o{ product_revisions : "has history"
```
