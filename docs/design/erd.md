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

    ORDERS {
        bigint id PK
        bigint user_id FK "주문자"
        decimal total_amount "총 주문 금액"
        datetime created_at
    }

    ORDER_ITEM {
        bigint id PK
        bigint order_id FK "소속 주문"
        bigint product_id "원본 상품 ID"
        varchar product_name "상품명 스냅샷"
        varchar brand_name "브랜드명 스냅샷"
        decimal price "단가 스냅샷"
        int quantity "주문 수량"
        datetime created_at
    }

    BRAND ||--o{ PRODUCT : ""
    USER ||--o{ LIKES : ""
    PRODUCT ||--o{ LIKES : ""
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
| ORDERS | Order | 사용자의 주문 | 삭제 불가 |
| ORDER_ITEM | Order | 주문 시점 상품 스냅샷 | 삭제 불가 |

## 제약 조건

| 테이블 | 제약 | 컬럼 | 설명 |
|--------|------|------|------|
| USER | UNIQUE | login_id | 로그인 ID 유일성 |
| BRAND | UNIQUE | name | 브랜드명 유일성 (삭제 포함) |
| LIKES | UNIQUE | user_id, product_id | 1인 1좋아요 보장 |
