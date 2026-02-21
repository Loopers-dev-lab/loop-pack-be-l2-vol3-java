---
name: aggregate-rules
description: "Aggregate 경계 규칙, Root 통한 접근, ID 참조, Aggregate 단위 Repository. 도메인 모델링이나 Entity 간 관계를 설계할 때 활성화한다."
---

Aggregate 설계와 경계 규칙을 다룬다. 도메인 객체 간 관계를 설정할 때 반드시 아래 원칙을 따른다.

## Aggregate란

관련된 Entity와 VO를 하나로 묶어, **데이터 일관성을 보장하는 단위**이다.

```
Order (Aggregate Root)
├── OrderItem (내부 Entity)
├── OrderItem (내부 Entity)
└── OrderItem (내부 Entity)

→ 외부에서는 반드시 Order(Root)를 통해서만 접근
→ OrderItem을 직접 수정하면 안 됨
→ Order가 전체의 일관성을 책임짐
```

## Aggregate 핵심 규칙

### 규칙 1: Aggregate Root를 통해서만 내부 접근

외부에서 Aggregate 내부 Entity를 직접 수정하지 않는다. 반드시 Root의 메서드를 통해 접근한다.

```java
// Bad: 외부에서 OrderItem을 직접 수정
orderItem.changeQuantity(5);

// Good: Aggregate Root를 통해 수정
order.changeItemQuantity(orderItemId, 5);
```

### 규칙 2: Aggregate 간 참조는 ID로만

다른 Aggregate의 객체를 직접 참조하지 않는다. ID로만 참조한다.

```java
// Bad: Aggregate 간 직접 객체 참조 → 강한 결합
public class Order {
    private User user;              // User 객체 직접 참조
    private List<Product> products; // Product 객체 직접 참조
}

// Good: ID로만 참조 → 약한 결합
public class Order {
    private Long userId;              // ID로만 참조
    private List<OrderItem> items;    // OrderItem에 productId + 스냅샷 보관
}
```

**ID 참조의 이점:**
- Aggregate 간 결합도를 낮춤
- 트랜잭션 범위를 최소화
- 스냅샷 보관 가능 (주문 시점 가격이 나중에 바뀌어도 주문 금액 유지)

### 규칙 3: Aggregate Root 단위로 Repository

내부 Entity는 별도 Repository를 갖지 않는다. Root의 Repository를 통해서만 영속화한다.

```java
// Good: Aggregate Root 단위 Repository
OrderRepository     → Order + OrderItem 함께 저장/조회
ProductRepository   → Product 저장/조회
InventoryRepository → Inventory 저장/조회

// Bad: 내부 Entity에 별도 Repository
OrderItemRepository → OrderItem은 Order를 통해서만 접근해야 함
```

### 규칙 4: 트랜잭션은 하나의 Aggregate 단위

하나의 트랜잭션에서 하나의 Aggregate만 수정하는 것이 원칙이다.
여러 Aggregate를 수정해야 하면 Application Layer(Facade)에서 조율한다.

---

## 프로젝트 Aggregate 경계 확정표

| Aggregate Root | 포함 객체 | 경계 설정 이유 |
|----------------|----------|-------------|
| **User** | User | 회원 정보 단독 관리. VO(LoginId, Password 등)는 Embedded |
| **UserAddress** | UserAddress | User와 생명주기 독립 (주소만 추가/삭제 가능) |
| **Brand** | Brand | 브랜드 단독 관리. 삭제 시 Product 연쇄는 Facade 레벨 조율 |
| **Product** | Product | 상품 단독 관리. Inventory와 생명주기 독립 (비관적 락 범위 분리) |
| **Inventory** | Inventory | 재고 예약/확정/해제의 생명주기가 Product 수정과 독립적 |
| **ProductLike** | ProductLike | Like의 생명주기는 Product와 독립. hard delete |
| **CartItem** | CartItem | 장바구니 전체에 대한 비즈니스 규칙 없음. 항목 단위 관리 |
| **Order** | Order, OrderItem | OrderItem은 Order 없이 존재 불가. 주문 생성 시 함께 생성 |
| **Payment** | Payment | 하나의 주문에 여러 결제 시도 가능 (재시도). 독립 관리 |
| **PointAccount** | PointAccount | 포인트 잔액 독립 도메인. User와 1:1이지만 책임 분리 |
| **CouponTemplate** | CouponTemplate | 쿠폰 정책 마스터. 독립 관리 |
| **IssuedCoupon** | IssuedCoupon | 발급 쿠폰 독립 Aggregate. 상태 전이 관리 |

---

## 설계 체크리스트

- [ ] 내부 Entity가 Aggregate Root를 거치지 않고 외부에서 직접 수정되지 않는가?
- [ ] 다른 Aggregate를 객체 참조가 아닌 ID로만 참조하는가?
- [ ] Aggregate Root 단위로만 Repository가 존재하는가?
- [ ] 하나의 트랜잭션에서 여러 Aggregate를 수정하려 하지 않는가?