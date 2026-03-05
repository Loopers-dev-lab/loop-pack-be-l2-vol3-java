# ERD

## 개요
커머스 서비스의 전체 데이터 구조를 정의한다.

## ERD

```mermaid
erDiagram
    USER {
        bigint id PK
        varchar login_id UK "로그인 ID"
        varchar password "암호화된 비밀번호"
        varchar name "이름"
        date birth_date "생년월일"
        varchar email "이메일"
        datetime created_at
        datetime updated_at
        datetime deleted_at "NULL=활성"
    }

    BRAND {
        bigint id PK
        varchar name UK "브랜드명"
        varchar description "설명"
        datetime created_at
        datetime updated_at
        datetime deleted_at "NULL=활성"
    }

    PRODUCT {
        bigint id PK
        bigint brand_id FK "소속 브랜드"
        varchar name "상품명"
        varchar description "설명"
        decimal price "가격"
        int stock_quantity "재고 수량"
        int like_count "좋아요 수"
        datetime created_at
        datetime updated_at
        datetime deleted_at "NULL=활성"
    }

    LIKES {
        bigint id PK
        bigint user_id FK "좋아요 사용자"
        bigint product_id FK "좋아요 상품"
        datetime created_at
    }

    COUPON {
        bigint id PK
        varchar name "쿠폰명"
        varchar type "FIXED / RATE"
        int value "할인값"
        decimal min_order_amount "최소 주문 금액"
        int max_issue_count "총 발급 수량"
        int issued_count "현재 발급 수량"
        datetime expired_at "만료일"
        datetime created_at
        datetime updated_at
        datetime deleted_at "NULL=활성"
    }

    ISSUED_COUPON {
        bigint id PK
        bigint coupon_id FK "소속 쿠폰"
        bigint user_id FK "발급 사용자"
        varchar coupon_name "쿠폰명 스냅샷"
        varchar coupon_type "FIXED / RATE 스냅샷"
        int coupon_value "할인값 스냅샷"
        decimal min_order_amount "최소 주문 금액 스냅샷"
        datetime expired_at "만료일 스냅샷"
        datetime used_at "사용일시"
        datetime created_at
        datetime updated_at
        datetime deleted_at "NULL=활성"
    }

    ORDERS {
        bigint id PK
        bigint user_id FK "주문자"
        decimal total_amount "쿠폰 적용 전 금액"
        decimal discount_amount "할인 금액"
        decimal final_amount "최종 결제 금액"
        bigint issued_coupon_id "적용된 발급 쿠폰"
        datetime created_at
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id FK "소속 주문"
        bigint product_id "원본 상품 ID"
        varchar product_name "상품명 스냅샷"
        decimal price "단가 스냅샷"
        int quantity "주문 수량"
        datetime created_at
    }

    BRAND ||--o{ PRODUCT : ""
    USER ||--o{ LIKES : ""
    PRODUCT ||--o{ LIKES : ""
    COUPON ||--o{ ISSUED_COUPON : ""
    USER ||--o{ ISSUED_COUPON : ""
    USER ||--o{ ORDERS : ""
    ORDERS ||--|{ ORDER_ITEM : ""
    ORDER_ITEM }o--|| PRODUCT : ""
```

## 테이블 설명

| 테이블 | 도메인 | 설명 | 삭제 정책 |
|--------|--------|------|----------|
| USER | User | 서비스 가입 사용자 계정 | Soft Delete |
| BRAND | Brand | 입점 브랜드 정보 | Soft Delete |
| PRODUCT | Product | 판매 상품 정보 | Soft Delete |
| LIKES | Like | 사용자-상품 간 좋아요 | Hard Delete |
| COUPON | Coupon | 할인 쿠폰 템플릿 | Soft Delete |
| ISSUED_COUPON | Coupon | 사용자에게 발급된 쿠폰 | Soft Delete |
| ORDERS | Order | 사용자의 주문 | 삭제 불가 |
| ORDER_ITEM | Order | 주문 시점 상품 스냅샷 | 삭제 불가 |

## 제약 조건

| 테이블 | 제약 | 컬럼 | 설명 |
|--------|------|------|------|
| USER | UNIQUE | login_id | 로그인 ID 유일성 |
| BRAND | UNIQUE | name | 브랜드명 유일성 (삭제 포함) |
| LIKES | UNIQUE | user_id, product_id | 1인 1좋아요 보장 |
| ISSUED_COUPON | UNIQUE | coupon_id, user_id | 1인 1매 보장 (활성 데이터 기준) |
