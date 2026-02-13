# ERD (Entity Relationship Diagram)

## 1. 개요

이커머스 플랫폼의 핵심 도메인 테이블 구조를 정의한다.

---

## 2. ERD 다이어그램

```mermaid
erDiagram
    member ||--o{ orders : "places"
    member ||--o{ likes : "has"
    brand ||--o{ product : "has"
    product ||--o{ likes : "has"
    product ||--o{ order_item : "referenced by"
    orders ||--|{ order_item : "contains"

    member {
        bigint id PK
        varchar login_id UK "로그인 ID"
        varchar password "암호화된 비밀번호"
        varchar name "이름"
        date birth_date "생년월일"
        varchar email "이메일"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "soft delete"
    }

    brand {
        bigint id PK
        varchar name "브랜드명"
        varchar description "브랜드 설명"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "soft delete"
    }

    product {
        bigint id PK
        bigint brand_id FK "브랜드 참조"
        varchar name "상품명"
        int price "가격 (원)"
        int stock_quantity "재고 수량"
        int like_count "좋아요 수 (비정규화)"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "soft delete"
    }

    likes {
        bigint id PK
        bigint member_id FK "회원 참조"
        bigint product_id FK "상품 참조"
        timestamp created_at
    }

    orders {
        bigint id PK
        bigint member_id FK "주문자 참조"
        varchar status "주문 상태 (CREATED/PAID/CANCELLED)"
        int total_price "총 주문 금액"
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at "soft delete"
    }

    order_item {
        bigint id PK
        bigint order_id FK "주문 참조"
        bigint product_id FK "상품 참조 (원본)"
        varchar product_name "상품명 스냅샷"
        int product_price "상품 가격 스냅샷"
        varchar brand_name "브랜드명 스냅샷"
        int quantity "주문 수량"
        timestamp created_at
    }
```

---

## 3. 테이블 상세 명세

### 3.1 member (회원)

> 1주차에 구현 완료. 참고용으로 포함.

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 회원 고유 ID |
| login_id | VARCHAR(50) | UK, NOT NULL | 로그인 ID |
| password | VARCHAR(255) | NOT NULL | 암호화된 비밀번호 |
| name | VARCHAR(50) | NOT NULL | 이름 |
| birth_date | DATE | NOT NULL | 생년월일 |
| email | VARCHAR(100) | NOT NULL | 이메일 |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | 삭제 일시 (soft delete) |

---

### 3.2 brand (브랜드)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 브랜드 고유 ID |
| name | VARCHAR(100) | NOT NULL | 브랜드명 |
| description | VARCHAR(500) | NULL | 브랜드 설명 |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | 삭제 일시 (soft delete) |

**인덱스**:
- `idx_brand_deleted_at`: deleted_at (목록 조회 시 필터링)

---

### 3.3 product (상품)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 상품 고유 ID |
| brand_id | BIGINT | FK (논리적) | 브랜드 참조 |
| name | VARCHAR(200) | NOT NULL | 상품명 |
| price | INT | NOT NULL, CHECK(price > 0) | 가격 (원) |
| stock_quantity | INT | NOT NULL, DEFAULT 0, CHECK(stock_quantity >= 0) | 재고 수량 |
| like_count | INT | NOT NULL, DEFAULT 0 | 좋아요 수 (비정규화) |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | 삭제 일시 (soft delete) |

**인덱스**:
- `idx_product_brand_id`: brand_id (브랜드별 상품 조회)
- `idx_product_like_count`: like_count DESC (인기순 정렬)
- `idx_product_deleted_at`: deleted_at (목록 조회 시 필터링)

**설계 결정**:
- `like_count`: 비정규화 컬럼. 목록 조회 시 COUNT 쿼리 대신 정렬에 사용
- 좋아요 추가/삭제 시 동기화. 오차 허용 가능 (배치로 보정 가능)

---

### 3.4 likes (좋아요)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 좋아요 고유 ID |
| member_id | BIGINT | FK (논리적), NOT NULL | 회원 참조 |
| product_id | BIGINT | FK (논리적), NOT NULL | 상품 참조 |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |

**인덱스**:
- `uk_likes_member_product`: (member_id, product_id) UNIQUE - 중복 좋아요 방지
- `idx_likes_member_id`: member_id (회원별 좋아요 목록 조회)
- `idx_likes_product_id`: product_id (상품별 좋아요 조회)

**설계 결정**:
- Hard Delete 사용 (soft delete 불필요)
- 상품/브랜드 삭제 시 연쇄 삭제

---

### 3.5 orders (주문)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 고유 ID |
| member_id | BIGINT | FK (논리적), NOT NULL | 주문자 참조 |
| status | VARCHAR(20) | NOT NULL | 주문 상태 |
| total_price | INT | NOT NULL, CHECK(total_price >= 0) | 총 주문 금액 |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |
| updated_at | TIMESTAMP | NOT NULL | 수정 일시 |
| deleted_at | TIMESTAMP | NULL | 삭제 일시 (soft delete) |

**인덱스**:
- `idx_orders_member_id`: member_id (회원별 주문 조회)
- `idx_orders_status`: status (상태별 필터링)
- `idx_orders_member_created_at`: (member_id, created_at) (회원별 날짜 범위 조회)

**주문 상태 값**:
| 상태 | 설명 |
|------|------|
| CREATED | 주문 생성됨 (현재는 이게 곧 완료) |
| PAID | 결제 완료 (미래 확장용) |
| CANCELLED | 취소됨 |

---

### 3.6 order_item (주문 항목)

| 컬럼명 | 타입 | 제약조건 | 설명 |
|--------|------|----------|------|
| id | BIGINT | PK, AUTO_INCREMENT | 주문 항목 고유 ID |
| order_id | BIGINT | FK (논리적), NOT NULL | 주문 참조 |
| product_id | BIGINT | FK (논리적), NOT NULL | 상품 참조 (원본) |
| product_name | VARCHAR(200) | NOT NULL | 상품명 **스냅샷** |
| product_price | INT | NOT NULL | 상품 가격 **스냅샷** |
| brand_name | VARCHAR(100) | NOT NULL | 브랜드명 **스냅샷** |
| quantity | INT | NOT NULL, CHECK(quantity > 0) | 주문 수량 |
| created_at | TIMESTAMP | NOT NULL | 생성 일시 |

**인덱스**:
- `idx_order_item_order_id`: order_id (주문별 항목 조회)

**설계 결정**:
- 스냅샷 컬럼: `product_name`, `product_price`, `brand_name`
- 원본이 삭제/변경되어도 주문 이력에서 조회 가능
- `product_id`는 원본 참조용으로 유지 (상품 페이지 이동 등)

---

## 4. 관계 요약

| 관계 | 카디널리티 | 설명 |
|------|-----------|------|
| member - orders | 1:N | 회원은 여러 주문 가능 |
| member - likes | 1:N | 회원은 여러 좋아요 가능 |
| brand - product | 1:N | 브랜드는 여러 상품 보유 |
| product - likes | 1:N | 상품은 여러 좋아요 받음 |
| product - order_item | 1:N | 상품은 여러 주문에 포함 |
| orders - order_item | 1:N | 주문은 여러 항목 포함 |

---

## 5. FK 제약 정책

| 관계 | FK 제약 | 이유 |
|------|---------|------|
| product → brand | 논리적 (제약 없음) | 브랜드 삭제 시 soft delete, 애플리케이션에서 검증 |
| likes → member/product | 논리적 | 상품 삭제 시 좋아요 연쇄 삭제, 애플리케이션 처리 |
| orders → member | 논리적 | 회원 삭제 시에도 주문 이력 보존 |
| order_item → orders | 논리적 | 주문과 항목은 항상 함께 관리 |
| order_item → product | 논리적 | 스냅샷이 있어 원본 삭제 가능 |

**참고**: 대규모 트래픽에서 FK 제약은 데드락, Cascading 이슈를 유발할 수 있어 논리적 관계로 설계. 데이터 정합성은 애플리케이션 레벨에서 보장.

---

## 6. DDL 예시

```sql
-- 브랜드 테이블
CREATE TABLE brand (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    INDEX idx_brand_deleted_at (deleted_at)
);

-- 상품 테이블
CREATE TABLE product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    brand_id BIGINT NOT NULL,
    name VARCHAR(200) NOT NULL,
    price INT NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    like_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    INDEX idx_product_brand_id (brand_id),
    INDEX idx_product_like_count (like_count DESC),
    INDEX idx_product_deleted_at (deleted_at),
    CONSTRAINT chk_product_price CHECK (price > 0),
    CONSTRAINT chk_product_stock CHECK (stock_quantity >= 0)
);

-- 좋아요 테이블
CREATE TABLE likes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_likes_member_product (member_id, product_id),
    INDEX idx_likes_member_id (member_id),
    INDEX idx_likes_product_id (product_id)
);

-- 주문 테이블
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_price INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    INDEX idx_orders_member_id (member_id),
    INDEX idx_orders_status (status),
    INDEX idx_orders_member_created_at (member_id, created_at),
    CONSTRAINT chk_orders_total_price CHECK (total_price >= 0)
);

-- 주문 항목 테이블
CREATE TABLE order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(200) NOT NULL,
    product_price INT NOT NULL,
    brand_name VARCHAR(100) NOT NULL,
    quantity INT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_order_item_order_id (order_id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0)
);
```

---

## 7. 잠재 리스크

| 리스크 | 현재 상태 | 대응 방안 |
|--------|----------|----------|
| **FK 제약 없음** | 논리적 관계만 정의 | 데이터 정합성은 애플리케이션에서 보장. 정기적 정합성 체크 배치 필요 |
| **like_count 비정규화** | product 테이블에 저장 | 정합성 오차 가능. COUNT 쿼리와 주기적 비교/보정 필요 |
| **soft delete 쿼리 복잡도** | WHERE deleted_at IS NULL 필수 | 조회 쿼리마다 조건 누락 위험. 기본 스코프 또는 뷰 활용 권장 |
| **order_item 스냅샷 중복** | 같은 상품 여러 주문 시 반복 저장 | 데이터 증가. 스냅샷 테이블 분리 또는 압축 고려 (대량 트래픽 시) |
| **인덱스 과다** | 정렬/필터용 여러 인덱스 | 쓰기 성능 저하 가능. 실제 쿼리 패턴 분석 후 최적화 |
| **orders.status VARCHAR** | 문자열 저장 | ENUM 타입으로 변경하거나 코드 테이블 분리 고려 |
