# ERD (Entity Relationship Diagram)

LAST UPDATED: 2026-03-03

## 목차
- [개요](#개요)
- [ERD](#erd)
- [설계 포인트](#설계-포인트)

## 개요

이 문서는 감성 이커머스 플랫폼의 데이터베이스 구조를 ERD(Entity Relationship Diagram)로 정의한다.
[03-class-diagram.md](./03-class-diagram.md)의 도메인 모델을 기반으로, 각 테이블의 컬럼, 제약 조건, 테이블 간 관계를 Mermaid ERD로 표현한다.
용어 정의는 [00-glossary.md](./00-glossary.md)를, 요구사항은 [01-requirements.md](./01-requirements.md)를 참고한다.

- 대상 도메인: 회원, 브랜드, 상품, 좋아요, 쿠폰, 주문

## ERD

```mermaid
erDiagram
    product {
        bigint id PK "not null"
        bigint brand_id FK "not null"
        varchar name "not null"
        varchar thumbnail_url "not null"
        bigint price "not null"
        bigint stock "not null"
        bigint like_count "not null, default 0"
        text description "null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }
    
    brand {
        bigint id PK "not null"
        varchar name "not null"
        varchar logo_url "not null"
        text description "null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }
    
    likes {
        bigint id PK "not null"
        bigint user_id FK "not null"
        bigint product_id FK "not null"
        timestamp liked_at "not null"
    }
    
    coupon {
        bigint id PK "not null"
        varchar name "not null"
        varchar type "not null"
        bigint discount_value "not null"
        bigint max_discount_price "null"
        bigint min_order_price "not null"
        timestamp expired_at "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }

    owned_coupon {
        bigint id PK "not null"
        bigint coupon_id FK "not null"
        bigint user_id FK "not null"
        bigint version "not null, default 0"
        timestamp used_at "null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }

    orders {
        bigint id PK "not null"
        bigint user_id FK "not null"
        bigint owned_coupon_id FK "null"
        varchar name "not null"
        varchar status "not null"
        bigint original_total_price "not null"
        bigint discount_amount "not null"
        bigint total_price "not null"
        timestamp ordered_at "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }
    
    order_item {
        bigint id PK "not null"
        bigint order_id FK "not null"
        bigint product_id FK "not null"
        varchar product_name "not null"
        bigint product_price "not null"
        varchar product_thumbnail_url "not null"
        bigint quantity "not null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }
    
    payment {
        bigint id PK "not null"
        bigint user_id FK "not null"
        bigint order_id FK "not null"
        varchar transaction_key "not null, unique"
        varchar card_type "not null"
        varchar card_no "not null"
        bigint amount "not null"
        varchar status "not null, default 'PENDING'"
        varchar reason "null"
        timestamp created_at "not null"
        timestamp updated_at "not null"
        timestamp deleted_at "null"
    }

    brand ||--o{ product: ""
    product ||--o{ likes: ""
    product ||--o{ order_item: ""
    orders ||--|{ order_item: ""
    orders ||--o{ payment: ""
    owned_coupon ||--o{ orders: ""
    coupon ||--o{ owned_coupon: ""
```

## 설계 포인트

### 좋아요 수 비정규화

- 좋아요 수를 `product.like_count` 컬럼에 비정규화하여 저장한다.
- 좋아요 등록/취소 시 `likes` 테이블과 `product.like_count`를 동일 트랜잭션에서 동기화한다.
- 조회 시 likes 테이블을 집계하지 않고 `product.like_count`를 직접 읽는다.
- 동시성 제어: 아토믹 업데이트(`UPDATE SET likeCount = likeCount ± 1`)로 lost update를 방지한다.
- 결정 배경과 상세: [ADR - likeCount 비정규화](../adr/01-like-count-denormalization.md) 참고.

### 좋아요 삭제 방식

- likes 테이블은 BaseEntity를 상속하지 않으며, 좋아요 취소 시 **물리 삭제(hard delete)**를 사용한다.
- 좋아요는 등록/취소가 빈번하고 이력 보존이 불필요하므로, insert/delete로 단순하게 처리한다.

### 유니크 제약 조건

- `likes`: `(user_id, product_id)` UNIQUE — 한 사용자가 동일 상품에 하나의 좋아요만 등록 가능
- `owned_coupon`: `(coupon_id, user_id)` UNIQUE — 한 사용자가 동일 쿠폰을 1매만 발급 가능

### 주문 상태

- `orders.status`는 `OrderStatus` enum을 문자열로 저장한다.
- 상태값: `CREATED` (주문 생성 시 초기 상태), `PAID` (결제 완료)
- 상태 전이: `CREATED` → `PAID` (결제 성공 콜백 수신 시)

### 결제

- `payment` 테이블은 PG사를 통한 카드 결제 정보를 저장한다.
- `transaction_key`는 PG사에서 발급하는 고유 식별자로, UNIQUE 제약 조건을 가진다.
- `status`는 `PaymentStatus` enum을 문자열로 저장한다. 상태값: `PENDING` (결제 요청), `SUCCESS` (결제 성공), `FAILED` (결제 실패)
- 하나의 주문에 여러 결제 시도가 가능하다 (결제 실패 후 재시도).

### 쿠폰 발급 동시성 제어

- 1인 1매 보장: `UNIQUE(coupon_id, user_id)` + `DataIntegrityViolationException` 처리

### 쿠폰 적용 스냅샷

- 주문 시점의 할인 정보(원가, 할인액, 결제액)를 `orders` 테이블에 저장한다.
- `original_total_price`: 할인 적용 전 주문 총액
- `discount_amount`: 실제 적용된 할인 금액 (쿠폰 미적용 시 0)
- `total_price`: 최종 결제 금액 (`original_total_price - discount_amount`)
- `owned_coupon_id`: 사용된 `OwnedCoupon.id`를 저장하며, 쿠폰 미적용 시 NULL

### 보유 쿠폰 상태 동적 판정

- `owned_coupon` 테이블에 `status` 컬럼을 두지 않는다. 상태는 `OwnedCoupon.getStatus()`에서 `used_at`과 `coupon.expired_at` 기준으로 실시간 판정한다.
  - `used_at IS NOT NULL` → USED
  - `coupon.expired_at` 경과 → EXPIRED
  - 그 외 → AVAILABLE
- 만료일 변경 시 `owned_coupon` 레코드를 일괄 업데이트할 필요가 없다.

