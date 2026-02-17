# 04. 전체 테이블 구조 및 관계 정리 (ERD)

---

## 설계 원칙

- **OrderSheet 제거**: Order 단일 테이블로 통합 (PENDING/PAID/EXPIRED/CANCELED)
- **소프트 삭제**: 모든 테이블에 `deleted_at` 컬럼 (좋아요 제외 - hard delete)
- **상태 관리**: `status` VARCHAR 컬럼으로 명확한 상태 전이 표현
- **가격**: `base_price` 단일 필드. 할인은 쿠폰 시스템으로 처리
- **재고**: `inventories` 테이블 분리 + 예약(reservation) 모델
- **재고 예약 만료**: 30분
- **FK 제약 미사용**: 논리 참조만 (DBML Ref)
- DBML 원본: `04-erd.dbml` 참조

---

## 1. 전체 ERD

```mermaid
erDiagram
    users ||--o{ user_addresses : "1:N"
    users ||--|| carts : "1:1"
    users ||--o{ product_likes : "1:N"
    users ||--o{ brand_likes : "1:N"
    users ||--o{ orders : "1:N"
    users ||--|| point_accounts : "1:1"
    users ||--o{ issued_coupons : "1:N"
    users ||--o{ inventory_reservations : "1:N"

    brands ||--o{ products : "1:N"
    brands ||--o{ brand_likes : "1:N"

    products ||--o{ product_likes : "1:N"
    products ||--|| inventories : "1:1"
    products ||--o{ cart_items : "1:N"
    products ||--o{ inventory_reservation_items : "1:N"

    carts ||--o{ cart_items : "1:N"

    inventory_reservations ||--o{ inventory_reservation_items : "1:N"
    inventory_reservations ||--|| orders : "1:1"

    orders ||--o{ order_items : "1:N"
    orders ||--o{ payments : "1:N"

    point_accounts ||--o{ point_ledgers : "1:N"

    coupon_templates ||--o{ issued_coupons : "1:N"
    coupon_templates ||--o{ coupon_targets : "1:N"

    orders ||--o{ issued_coupons : "0:N redeemed"
    orders ||--o{ point_ledgers : "0:N"

    users {
        bigint id PK
        varchar login_id UK
        varchar email UK
        varchar password
        varchar name
        date birth_date
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    user_addresses {
        bigint id PK
        bigint user_id FK
        varchar receiver_name
        varchar phone
        varchar zip_code
        varchar address_line1
        varchar address_line2
        boolean is_default
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    brands {
        bigint id PK
        varchar name
        text description
        varchar status "ACTIVE/INACTIVE"
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    products {
        bigint id PK
        bigint brand_id FK
        varchar name
        text description
        int base_price
        int like_count
        varchar status "ACTIVE/SOLDOUT/HIDDEN/DISCONTINUED"
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    product_likes {
        bigint id PK
        bigint user_id FK
        bigint product_id FK
        timestamp created_at
    }

    brand_likes {
        bigint id PK
        bigint user_id FK
        bigint brand_id FK
        timestamp created_at
    }

    carts {
        bigint id PK
        bigint user_id UK
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    cart_items {
        bigint id PK
        bigint cart_id FK
        bigint product_id FK
        int quantity
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    inventories {
        bigint id PK
        bigint product_id UK
        int quantity
        int reserved_qty
        int safety_stock
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    inventory_reservations {
        bigint id PK
        bigint user_id FK
        varchar status "HELD/COMMITTED/RELEASED/EXPIRED"
        timestamp expires_at
        timestamp created_at
        timestamp updated_at
    }

    inventory_reservation_items {
        bigint id PK
        bigint reservation_id FK
        bigint product_id FK
        int quantity
        timestamp created_at
    }

    orders {
        bigint id PK
        varchar order_number UK
        bigint user_id FK
        bigint reservation_id UK
        varchar status "PENDING/PAID/EXPIRED/CANCELED"
        varchar orderer_name
        varchar receiver_name
        int subtotal_amount
        int discount_amount
        int point_used_amount
        int shipping_fee
        int total_amount
        bigint payment_id
        timestamp expires_at
        timestamp ordered_at
        timestamp created_at
        timestamp updated_at
    }

    order_items {
        bigint id PK
        bigint order_id FK
        bigint product_id "FK 아님"
        varchar product_name
        varchar brand_name
        int unit_price
        int discounted_unit_price
        int quantity
        int line_total
        timestamp created_at
    }

    payments {
        bigint id PK
        bigint order_id FK
        varchar status "REQUESTED/APPROVED/FAILED/CANCELED"
        varchar payment_method
        int requested_amount
        int approved_amount
        varchar pg_txn_id
        varchar idempotency_key UK
        timestamp requested_at
        timestamp approved_at
        timestamp created_at
        timestamp updated_at
    }

    point_accounts {
        bigint id PK
        bigint user_id UK
        int balance
        timestamp created_at
        timestamp updated_at
    }

    point_ledgers {
        bigint id PK
        bigint user_id FK
        bigint order_id
        varchar type "EARN/USE/REFUND/ADJUST"
        int amount
        int balance_after
        timestamp created_at
    }

    coupon_templates {
        bigint id PK
        varchar name
        varchar discount_type "FIXED/PERCENT"
        int discount_value
        int min_order_amount
        varchar status "ACTIVE/INACTIVE/EXPIRED"
        timestamp valid_from
        timestamp valid_until
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    issued_coupons {
        bigint id PK
        bigint user_id FK
        bigint coupon_template_id FK
        varchar code UK
        varchar status "ISSUED/RESERVED/REDEEMED/EXPIRED/CANCELED"
        bigint redeemed_order_id
        timestamp issued_at
        timestamp deleted_at
        timestamp created_at
        timestamp updated_at
    }

    coupon_targets {
        bigint id PK
        bigint coupon_template_id FK
        varchar target_type "ALL/PRODUCT/BRAND"
        bigint target_id
        timestamp deleted_at
        timestamp created_at
    }
```

---

## 2. 테이블 상세 명세

### 2-1. users (회원) - v1 완성

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 사용자 PK |
| login_id | VARCHAR(80) | UNIQUE, NOT NULL | 로그인 아이디 |
| email | VARCHAR(255) | UNIQUE, NOT NULL | 이메일 |
| password | VARCHAR(255) | NOT NULL | 비밀번호 해시 |
| name | VARCHAR(100) | NOT NULL | 표시명 |
| birth_date | DATE | | 생년월일 |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-2. user_addresses (주소)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주소 PK |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| receiver_name | VARCHAR(100) | | 수령인 이름 |
| phone | VARCHAR(50) | | 연락처 |
| zip_code | VARCHAR(20) | | 우편번호 |
| address_line1 | VARCHAR(255) | | 주소1 |
| address_line2 | VARCHAR(255) | | 상세주소 |
| is_default | BOOLEAN | | 기본주소 여부 |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**인덱스**: `idx_user_addresses_user_id(user_id)`, `idx_user_addresses_deleted_at(deleted_at)`

---

### 2-3. brands (브랜드)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 브랜드 PK |
| name | VARCHAR(150) | NOT NULL | 브랜드명 |
| description | TEXT | | 브랜드 설명 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | BRAND_STATUS |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**인덱스**: `idx_brands_status(status)`, `idx_brands_deleted_at(deleted_at)`

---

### 2-4. products (상품)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 상품 PK |
| brand_id | BIGINT | NOT NULL | 소속 브랜드 |
| name | VARCHAR(200) | NOT NULL | 상품명 |
| description | TEXT | | 상세 설명 |
| base_price | INT | NOT NULL | 기본 가격 (할인은 쿠폰으로 처리) |
| like_count | INT | NOT NULL, DEFAULT 0 | 좋아요 수 (정렬용 비정규화) |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | PRODUCT_STATUS |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**인덱스**: `idx_products_brand_id(brand_id)`, `idx_products_status(status)`, `idx_products_deleted_at(deleted_at)`, `idx_products_created_at(created_at)`, `idx_products_base_price(base_price)`, `idx_products_like_count(like_count)`

**설계 결정**:
- `base_price` 단일 필드: 상시 할인가 없음. 할인은 쿠폰 시스템으로만 처리
- `like_count` 비정규화: 좋아요순 정렬 성능. 등록/취소 시 동기 증감
- 재고는 `inventories` 테이블에서 분리 관리

---

### 2-5. product_likes (상품 좋아요) - hard delete

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 좋아요 PK |
| user_id | BIGINT | NOT NULL | 사용자 |
| product_id | BIGINT | NOT NULL | 대상 상품 |
| created_at | TIMESTAMP | NOT NULL | 좋아요 시각 |

**유니크 제약**: `uk_product_likes(user_id, product_id)`

**인덱스**: `idx_product_likes_user_id(user_id)`, `idx_product_likes_product_id(product_id)`

**hard delete 정책**: 이력 추적 불필요. 좋아요 취소 시 row 삭제. UK 충돌 문제 없음.

---

### 2-6. brand_likes (브랜드 좋아요) - hard delete

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 좋아요 PK |
| user_id | BIGINT | NOT NULL | 사용자 |
| brand_id | BIGINT | NOT NULL | 대상 브랜드 |
| created_at | TIMESTAMP | NOT NULL | 좋아요 시각 |

**유니크 제약**: `uk_brand_likes(user_id, brand_id)`

---

### 2-7. carts (장바구니)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 장바구니 PK |
| user_id | BIGINT | UNIQUE, NOT NULL | 사용자 ID (1:1) |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-8. cart_items (장바구니 항목)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 항목 PK |
| cart_id | BIGINT | NOT NULL | 장바구니 ID |
| product_id | BIGINT | NOT NULL | 상품 ID |
| quantity | INT | NOT NULL | 수량 |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**유니크 제약**: `uk_cart_items(cart_id, product_id)` - 동일 상품 추가 시 수량 merge

---

### 2-9. inventories (재고)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 재고 PK |
| product_id | BIGINT | UNIQUE, NOT NULL | 상품 ID (1:1) |
| quantity | INT | NOT NULL, DEFAULT 0 | 총 재고 수량 |
| reserved_qty | INT | NOT NULL, DEFAULT 0 | 예약 수량 |
| safety_stock | INT | NOT NULL, DEFAULT 0 | 안전재고 기준 |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**가용 재고 계산**: `available = quantity - reserved_qty`

**재고 예약 흐름**:
1. 주문 생성(PENDING) → `reserved_qty += 수량` (HELD)
2. 결제 성공(PAID) → `quantity -= 수량`, `reserved_qty -= 수량` (COMMITTED)
3. 결제 실패/만료 → `reserved_qty -= 수량` (RELEASED/EXPIRED)

---

### 2-10. inventory_reservations (재고 예약)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 예약 PK |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| status | VARCHAR(20) | NOT NULL | HELD/COMMITTED/RELEASED/EXPIRED |
| expires_at | TIMESTAMP | NOT NULL | 만료 시각 (생성 + 30분) |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-11. inventory_reservation_items (예약 항목)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 예약항목 PK |
| reservation_id | BIGINT | NOT NULL | 재고예약 ID |
| product_id | BIGINT | NOT NULL | 상품 ID |
| quantity | INT | NOT NULL | 예약 수량 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

---

### 2-12. orders (주문 - OrderSheet 통합)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 PK |
| order_number | VARCHAR(50) | UNIQUE, NOT NULL | 주문번호(외부노출) |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| reservation_id | BIGINT | UNIQUE, NOT NULL | 재고예약 ID (1:1) |
| status | VARCHAR(20) | NOT NULL | PENDING/PAID/EXPIRED/CANCELED |
| orderer_name | VARCHAR(100) | NOT NULL | 주문자 이름 |
| orderer_phone | VARCHAR(50) | NOT NULL | 주문자 연락처 |
| receiver_name | VARCHAR(100) | NOT NULL | 수령인 이름 |
| receiver_phone | VARCHAR(50) | NOT NULL | 수령인 연락처 |
| zip_code | VARCHAR(20) | NOT NULL | 우편번호 |
| address_line1 | VARCHAR(255) | NOT NULL | 주소1 |
| address_line2 | VARCHAR(255) | | 상세주소 |
| subtotal_amount | INT | NOT NULL | 상품 합계 |
| discount_amount | INT | NOT NULL, DEFAULT 0 | 쿠폰 할인 합계 |
| point_used_amount | INT | NOT NULL, DEFAULT 0 | 포인트 사용액 |
| shipping_fee | INT | NOT NULL, DEFAULT 0 | 배송비 |
| total_amount | INT | NOT NULL | 최종 결제 금액 |
| payment_method | VARCHAR(30) | | 결제수단 |
| payment_id | BIGINT | | 승인된 결제 ID |
| ordered_at | TIMESTAMP | | 결제 완료 시각 |
| expires_at | TIMESTAMP | NOT NULL | 결제 만료 시각 |
| canceled_at | TIMESTAMP | | 취소 시각 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**인덱스**: `uk_orders_order_number`, `uk_orders_reservation_id`, `idx_orders_user_id_status`, `idx_orders_status_expires_at`, `idx_orders_user_id_created_at`

**OrderSheet 통합 설계**:
- `PENDING` 상태가 기존 OrderSheet의 역할을 대체
- `PENDING`에서만 쿠폰/포인트 적용, 배송지 수정 가능
- `PAID` 전환 시 스냅샷 확정 (수정 불가)
- `total_amount = subtotal_amount - discount_amount - point_used_amount + shipping_fee`

---

### 2-13. order_items (주문항목 - 스냅샷)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 항목 PK |
| order_id | BIGINT | NOT NULL | 주문 ID |
| product_id | BIGINT | NOT NULL | 상품 ID (FK 아님) |
| product_name | VARCHAR(200) | NOT NULL | 상품명 스냅샷 |
| brand_name | VARCHAR(150) | NOT NULL | 브랜드명 스냅샷 |
| unit_price | INT | NOT NULL | 단가 스냅샷 (base_price) |
| discounted_unit_price | INT | NOT NULL | 할인 적용 단가 |
| quantity | INT | NOT NULL | 수량 |
| line_total | INT | NOT NULL | 라인 금액 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

**설계 의도**: 주문 시점의 상품 정보를 고정 저장. 원본 상품이 변경/삭제되어도 유지.

---

### 2-14. payments (결제)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 결제 PK |
| order_id | BIGINT | NOT NULL | 주문 ID |
| status | VARCHAR(20) | NOT NULL | REQUESTED/APPROVED/FAILED/CANCELED |
| payment_method | VARCHAR(30) | NOT NULL | 결제수단 |
| requested_amount | INT | NOT NULL | 요청 금액 |
| approved_amount | INT | | 승인 금액 |
| pg_txn_id | VARCHAR(100) | | PG 거래 식별자 |
| idempotency_key | VARCHAR(80) | UNIQUE | 멱등키 |
| requested_at | TIMESTAMP | NOT NULL | 요청 시각 |
| approved_at | TIMESTAMP | | 승인 시각 |
| failed_at | TIMESTAMP | | 실패 시각 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

**설계 결정**: 하나의 주문에 여러 결제 시도 가능 (실패 → 재시도). `orders.payment_id`는 최종 승인된 결제만 가리킴.

---

### 2-15. point_accounts (포인트 계좌)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 계좌 PK |
| user_id | BIGINT | UNIQUE, NOT NULL | 사용자 ID (1:1) |
| balance | INT | NOT NULL, DEFAULT 0 | 현재 잔액 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-16. point_ledgers (포인트 원장)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 원장 PK |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| order_id | BIGINT | | 주문 ID (관련 시) |
| type | VARCHAR(20) | NOT NULL | EARN/USE/REFUND/ADJUST |
| amount | INT | NOT NULL | 변동 금액(+/-) |
| balance_after | INT | NOT NULL | 반영 후 잔액 |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

---

### 2-17. coupon_templates (쿠폰 템플릿)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 템플릿 PK |
| name | VARCHAR(200) | NOT NULL | 쿠폰명 |
| description | TEXT | | 쿠폰 설명 |
| discount_type | VARCHAR(20) | NOT NULL | FIXED/PERCENT |
| discount_value | INT | NOT NULL | 할인 값 |
| max_discount_amount | INT | | 최대 할인액 (정률용) |
| min_order_amount | INT | NOT NULL | 최소 주문금액 |
| valid_from | TIMESTAMP | NOT NULL | 유효 시작일 |
| valid_until | TIMESTAMP | NOT NULL | 유효 종료일 |
| issue_limit | INT | | 전체 발급 제한 |
| per_user_limit | INT | | 유저별 발급 제한 |
| status | VARCHAR(20) | NOT NULL | ACTIVE/INACTIVE/EXPIRED |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-18. issued_coupons (발급 쿠폰)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 발급 쿠폰 PK |
| user_id | BIGINT | NOT NULL | 사용자 ID |
| coupon_template_id | BIGINT | NOT NULL | 쿠폰 템플릿 ID |
| code | VARCHAR(100) | UNIQUE, NOT NULL | 쿠폰 코드 |
| status | VARCHAR(20) | NOT NULL | ISSUED/RESERVED/REDEEMED/EXPIRED/CANCELED |
| issued_at | TIMESTAMP | NOT NULL | 발급 시각 |
| redeemed_order_id | BIGINT | | 사용 확정 주문 ID |
| expired_at | TIMESTAMP | | 만료 시각 |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |
| updated_at | TIMESTAMP | NOT NULL | 수정 시각 |

---

### 2-19. coupon_targets (쿠폰 대상)

| 컬럼 | 타입 | 제약 | 설명 |
|------|------|------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 대상 PK |
| coupon_template_id | BIGINT | NOT NULL | 쿠폰 템플릿 ID |
| target_type | VARCHAR(20) | NOT NULL | ALL/PRODUCT/BRAND |
| target_id | BIGINT | | 대상 ID (ALL이면 NULL) |
| deleted_at | TIMESTAMP | NULL | soft delete |
| created_at | TIMESTAMP | NOT NULL | 생성 시각 |

---

## 3. 제약 조건 및 정합성 정책

| 테이블 | 제약 | 유형 | 설명 |
|--------|------|------|------|
| products | brand_id → brands.id | 논리 FK | 상품은 반드시 브랜드에 소속 |
| product_likes | (user_id, product_id) | UK | 중복 좋아요 방지 |
| brand_likes | (user_id, brand_id) | UK | 중복 좋아요 방지 |
| carts | user_id | UK | 사용자당 장바구니 1개 |
| cart_items | (cart_id, product_id) | UK | 동일 상품 중복 방지 |
| inventories | product_id | UK | 상품당 재고 1건 (1:1) |
| inventories | quantity >= 0, reserved_qty >= 0 | 앱 검증 | 음수 방지 |
| orders | order_number | UK | 주문번호 유일성 |
| orders | reservation_id | UK | 주문당 예약 1건 (1:1) |
| order_items | product_id | 참조만 | FK 아님 - 스냅샷 유지 |
| payments | idempotency_key | UK | PG 중복 요청 방지 |
| issued_coupons | code | UK | 쿠폰 코드 유일성 |

---

## 4. 소프트 삭제 정책

| 테이블 | soft delete | 정책 |
|--------|-----------|------|
| users | deleted_at | v1 완성 |
| user_addresses | deleted_at | 주소 삭제 |
| brands | deleted_at | 삭제 시 소속 products + inventories 연쇄 soft delete |
| products | deleted_at | 삭제되어도 기존 order_items 스냅샷 유지 |
| **product_likes** | **hard delete** | 취소 시 row 삭제. 이력 불필요 |
| **brand_likes** | **hard delete** | 취소 시 row 삭제. 이력 불필요 |
| carts | deleted_at | |
| cart_items | deleted_at | 장바구니 상품 제거 |
| inventories | deleted_at | 상품 삭제 시 함께 soft delete |
| inventory_reservations | 상태 관리 | HELD→COMMITTED/RELEASED/EXPIRED 상태 전이로 관리 |
| orders | 상태 관리 | 취소는 status=CANCELED. deleted_at 미사용 |
| order_items | 없음 | 스냅샷 - 변경/삭제 불가 |
| payments | 상태 관리 | 상태 전이로 관리 |
| point_accounts | 없음 | 계좌 삭제 없음 |
| point_ledgers | 없음 | append-only |
| coupon_templates | deleted_at | |
| issued_coupons | deleted_at + 상태 | 상태 전이 + soft delete 병행 |
| coupon_targets | deleted_at | |

---

## 5. 인덱스 전략

| 테이블 | 인덱스 | 용도 |
|--------|--------|------|
| products | `idx_products_brand_id` | 브랜드별 상품 필터링 |
| products | `idx_products_status` | 상태별 조회 |
| products | `idx_products_created_at` | 최신순 정렬 |
| products | `idx_products_base_price` | 가격순 정렬 |
| products | `idx_products_like_count` | 좋아요순 정렬 |
| product_likes | `uk_product_likes(user_id, product_id)` | 중복 방지 + 존재 여부 |
| brand_likes | `uk_brand_likes(user_id, brand_id)` | 중복 방지 + 존재 여부 |
| inventories | `uk_inventories_product_id` | 상품당 재고 1건 |
| inventory_reservations | `idx_reservations_status_expires_at` | 배치 만료 처리 |
| orders | `uk_orders_order_number` | 주문번호 조회 |
| orders | `idx_orders_user_id_created_at` | 사용자별 주문 목록 |
| orders | `idx_orders_status_expires_at` | 배치 만료 처리 |
| order_items | `idx_order_items_order_id` | 주문별 항목 조회 |
| payments | `uk_payments_idempotency_key` | 멱등성 보장 |
| issued_coupons | `uk_issued_coupons_code` | 쿠폰 코드 조회 |
| issued_coupons | `idx_issued_coupons_user_id_status` | 사용자별 쿠폰 목록 |

---

## 6. 상태 전이 다이어그램

### Order 상태

```
PENDING ──결제성공──→ PAID
   │                    │
   ├──30분경과──→ EXPIRED   (배치)
   │
   └──결제실패/취소──→ CANCELED
```

### InventoryReservation 상태

```
HELD ──결제성공──→ COMMITTED
  │
  ├──결제실패──→ RELEASED
  │
  └──30분경과──→ EXPIRED   (배치)
```

### IssuedCoupon 상태

```
ISSUED ──주문적용──→ RESERVED ──결제성공──→ REDEEMED
  │                    │
  ├──유효기간만료──→ EXPIRED    ├──결제실패──→ ISSUED (복구)
  │
  └──관리자취소──→ CANCELED
```

### Payment 상태

```
REQUESTED ──PG승인──→ APPROVED
     │
     └──PG거절──→ FAILED
                    │
          APPROVED ──취소──→ CANCELED
```