# 전체 설계 조감도

> 전체 구조 파악용. 각 도메인의 상세 스펙은 `{domain}/DESIGN.md` 참조.

---

## 전체 ERD

> FK 제약조건은 사용하지 않는다. 관계선은 논리적 참조 관계를 나타내며, 실제 DB에서는 ID 컬럼으로만 참조한다.

```mermaid
erDiagram
    users {
        bigint id PK
        varchar login_id UK
        varchar password
        varchar name
        date birth_date
        varchar email
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    brands {
        bigint id PK
        varchar name UK
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    products {
        bigint id PK
        bigint brand_id
        varchar name
        int price
        int stock
        int like_count
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    likes {
        bigint id PK
        bigint user_id
        bigint product_id
        timestamp created_at
    }

    cart_items {
        bigint id PK
        bigint user_id
        bigint product_id
        int quantity
        timestamp created_at
        timestamp updated_at
    }

    orders {
        bigint id PK
        bigint user_id
        int total_price
        int original_total_price
        varchar status
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    order_items {
        bigint id PK
        bigint order_id
        bigint product_id
        varchar product_name
        varchar brand_name
        varchar image_url
        int order_price
        int quantity
        varchar status
        timestamp created_at
        timestamp updated_at
        timestamp deleted_at
    }

    brands ||--o{ products : ""
    users ||--o{ likes : ""
    products ||--o{ likes : ""
    users ||--o{ cart_items : ""
    products ||--o{ cart_items : ""
    users ||--o{ orders : ""
    orders ||--|{ order_items : ""
```

---

## 전체 클래스 다이어그램

```mermaid
classDiagram
    class User {
        String loginId
        String password
        String name
        LocalDate birthDate
        String email
    }

    class Brand {
        String name
        +update(String) void
        +softDelete() void
    }

    class Product {
        Long brandId
        String name
        int price
        int stock
        int likeCount
        +decreaseStock(int) void
        +isSoldOut() boolean
        +addLikeCount() void
        +subtractLikeCount() void
        +softDelete() void
    }

    class ProductLike {
        Long userId
        Long productId
    }

    class CartItem {
        Long userId
        Long productId
        int quantity
        +addQuantity(int) void
        +updateQuantity(int) void
    }

    class Cart {
        <<일급 컬렉션>>
        List~CartItem~ items
        +getTotalPrice() int
        +selectItems(List~Long~) List~CartItem~
    }

    class Order {
        Long userId
        List~OrderItem~ items
        int totalPrice
        int originalTotalPrice
        OrderStatus status
        +create(Long userId, List~OrderItem~ items) Order
        +addItem(OrderItem item) void
        +cancelItem(Long orderItemId) OrderItem
        +recalculateTotalPrice() void
        +validateOwner(Long userId) void
    }

    class OrderItem {
        Order order
        Long productId
        ProductSnapshot productSnapshot
        int orderPrice
        int quantity
        OrderItemStatus status
        +create(Long productId, int orderPrice, int quantity, ProductSnapshot snapshot) OrderItem
        +cancel() void
        +assignOrder(Order order) void
    }

    class ProductSnapshot {
        <<Embeddable>>
        String productName
        String brandName
        String imageUrl
    }

    class OrderStatus {
        <<enumeration>>
        ORDERED
        CANCELLED
    }

    class OrderItemStatus {
        <<enumeration>>
        ORDERED
        CANCELLED
    }

    Product "*" --> "1" Brand : ID 참조 (brandId)
    ProductLike "*" --> "1" User : userId
    ProductLike "*" --> "1" Product : productId
    CartItem "*" --> "1" User : userId
    CartItem "*" --> "1" Product : productId
    Cart o-- CartItem : 일급 컬렉션
    Order "*" --> "1" User : userId
    Order "1" *-- "*" OrderItem : @OneToMany (양방향)
    Order --> OrderStatus
    OrderItem *-- ProductSnapshot : @Embedded
    OrderItem --> OrderItemStatus
```

---

## 도메인 간 관계 요약

| 관계 | 카디널리티 | 참조 방식 | 비고 |
|---|---|---|---|
| Brand → Product | 1 : N | ID 참조 (brandId) | 도메인 간 패키지 격벽 유지 |
| User → ProductLike | 1 : N | ID 참조 (userId) | UNIQUE(userId, productId) |
| Product → ProductLike | 1 : N | ID 참조 (productId) | 교차 테이블 |
| User → CartItem | 1 : N | ID 참조 (userId) | UNIQUE(userId, productId) |
| Product → CartItem | 1 : N | ID 참조 (productId) | 가격 저장 안 함 |
| User → Order | 1 : N | ID 참조 (userId) | UserSnapshot 불필요 |
| Order ↔ OrderItem | 1 : N | 양방향 `@OneToMany` / `@ManyToOne` | 같은 Aggregate. cascade=PERSIST, @BatchSize(100) |
| OrderItem → ProductSnapshot | - | `@Embedded` (`@Embeddable` VO) | 주문 시점 스냅샷을 개념 단위로 그룹핑 |

---

## 제약조건 전체

| 테이블 | 제약조건 | 설명 |
|---|---|---|
| users | UNIQUE(login_id) | 로그인 ID 중복 방지 |
| brands | UNIQUE(name) | 브랜드명 중복 방지 (409 Conflict) |
| likes | UNIQUE(user_id, product_id) | 1인 1좋아요 보장 |
| cart_items | UNIQUE(user_id, product_id) | 동일 상품 중복 담기 방지 |

---

## 인덱스 전체

| 테이블 | 인덱스 컬럼 | 용도 |
|---|---|---|
| products | brand_id | 브랜드별 상품 필터링 |
| likes | user_id | 유저의 좋아요 목록 조회 |
| cart_items | user_id | 유저의 장바구니 조회 |
| orders | (user_id, created_at) | 유저의 주문 목록 조회 |
| order_items | order_id | 주문의 상세 항목 조회 |

---

## 동시성 제어 전체

| 대상 | 방식 | 이유 |
|---|---|---|
| products.stock | 비관적 락 (추후 확정) | 재고 음수 방지 |
| products.like_count | 원자적 UPDATE | 경합 낮음 |
| likes | DB UNIQUE 제약 | 더블클릭 중복 방지 |
| cart_items | DB UNIQUE 제약 | 더블클릭 중복 방지 |
