> **TL;DR**: Aggregate Root가 자식의 생성을 통제해야 한다. "규칙을 문서에 쓰는 것"보다 "컴파일러가 잡게 만드는 것"이 확실하다. package-private 생성자 하나로 설계 의도를 코드에 강제할 수 있다.

---

## Facade가 OrderItem을 만들고 있었다

3주차 주문 도메인을 구현하고 리뷰하다가 한 가지가 걸렸다.

```java
// OrderFacade.java (Application Layer)
List<OrderItem> orderItems = new ArrayList<>();
for (int i = 0; i < itemRequests.size(); i++) {
    orderItems.add(new OrderItem(
        product.getId(), product.getName(), product.getPrice().getValue(),
        brandName, itemRequests.get(i).quantity()
    ));
}
return orderRepository.save(Order.create(memberId, orderItems));
```

Facade가 `new OrderItem()`을 직접 호출하고 있다. OrderItem은 Order의 자식 엔티티인데, Application Layer에서 마음대로 생성하고 있는 거다.

지금 당장 동작에 문제는 없다. 하지만 이건 **"Aggregate Root가 자식의 라이프사이클을 통제한다"는 원칙**에 어긋난다.

---

## Aggregate Root는 왜 자식을 직접 만들어야 하는가

DDD에서 Aggregate는 **일관성 경계**다. Order와 OrderItem은 항상 함께 생성되고, 함께 저장되고, Order를 통해서만 접근해야 한다.

이 원칙이 깨지면 어떤 일이 벌어질까?

### 시나리오: 6개월 후 신입 개발자가 합류한다

OrderItem의 생성자가 `public`이니까, 아무 곳에서나 쓸 수 있다.

```java
// 어떤 새로운 서비스에서
OrderItem item = new OrderItem(productId, name, price, brand, qty);
// Order 없이 OrderItem만 떠도는 상황
// totalPrice 계산 로직을 우회
// 비즈니스 규칙(주문 생성 시 검증)을 건너뜀
```

코드 리뷰에서 잡을 수 있다? 물론이다. 하지만 사람은 실수한다. **컴파일러가 잡아주는 게 사람이 잡는 것보다 싸고 확실하다.**

---

## 해결: 두 가지를 바꿨다

### 1. OrderItem 생성자를 package-private로 변경

```java
// OrderItem.java
OrderItem(Long productId, String productName, int productPrice,
          String brandName, int quantity) {
    // public → package-private (접근 제한자 제거)
}
```

이제 `com.loopers.domain.order` 패키지 외부에서는 `new OrderItem()`이 **컴파일 에러**가 된다. Facade, Controller, 어떤 서비스에서도 직접 생성할 수 없다.

### 2. Order.create()에 스냅샷 데이터를 전달

Order 내부에 `ItemSnapshot` record를 정의하고, 외부에서는 이 스냅샷만 넘기도록 했다.

```java
// Order.java
public record ItemSnapshot(
    Long productId, String productName, int productPrice,
    String brandName, int quantity
) {}

public static Order create(Long memberId, List<ItemSnapshot> snapshots) {
    Order order = new Order();
    order.memberId = memberId;
    order.status = OrderStatus.CREATED;
    for (ItemSnapshot s : snapshots) {
        order.items.add(new OrderItem(
            s.productId(), s.productName(), s.productPrice(),
            s.brandName(), s.quantity()
        ));
    }
    order.totalPrice = order.items.stream()
        .mapToInt(OrderItem::getSubtotal).sum();
    return order;
}
```

Facade는 이제 데이터만 전달하고, 생성은 Order가 한다.

```java
// OrderFacade.java — 변경 후
snapshots.add(new Order.ItemSnapshot(
    product.getId(), product.getName(),
    product.getPrice().getValue(), brandName,
    itemRequests.get(i).quantity()
));
return orderRepository.save(Order.create(memberId, snapshots));
```

---

## 접근 제어 수준을 왜 package-private으로 했는가

| 접근 수준 | 누가 쓸 수 있나 | 판단 |
|---|---|---|
| `public` | 누구나 | 과도함 — Aggregate Root 우회 가능 |
| **package-private** | **같은 패키지 (Order, OrderItem)** | **적절 — Order만 생성 가능** |
| `protected` | 같은 패키지 + 하위 클래스 | 상속 목적 아니면 불필요 |
| `private` | OrderItem 자기 자신만 | 과도함 — Order도 못 씀 |

Java에서 패키지가 Aggregate 경계 역할을 한다. `com.loopers.domain.order` 패키지 안에 Order와 OrderItem이 함께 있으니, package-private이 딱 맞는 수준이다.

---

## 테스트는 어떻게 되나

테스트 클래스도 같은 패키지(`com.loopers.domain.order`)에 위치하기 때문에 package-private 생성자에 접근할 수 있다. 하지만 `Order.create()`가 `ItemSnapshot`을 받도록 바뀌었으니, 테스트도 자연스럽게 스냅샷 기반으로 전환했다.

```java
// OrderTest.java
Order.ItemSnapshot snap1 = new Order.ItemSnapshot(1L, "상품A", 10000, "브랜드A", 2);
Order.ItemSnapshot snap2 = new Order.ItemSnapshot(2L, "상품B", 5000, "브랜드B", 3);
Order order = Order.create(1L, List.of(snap1, snap2));
```

OrderItemTest는 같은 패키지이므로 직접 생성자를 호출해서 단위 테스트할 수 있다. 이건 의도된 설계다 — 같은 Aggregate 내부에서는 접근이 자유로워야 한다.

---

## 돌아보며

처음에는 "Facade에서 OrderItem 만드는 게 뭐가 문제지?"라고 생각했다. 동작은 똑같으니까. 하지만 **설계는 "지금 동작하느냐"가 아니라 "6개월 후에도 의도대로 사용되느냐"의 문제**다.

package-private 생성자 하나 바꾼 것뿐인데, 효과는 크다.

- **Aggregate Root(Order)가 자식(OrderItem)의 생성을 통제**한다
- **외부에서 우회할 수 없다** — 컴파일 타임에 강제된다
- **Facade는 데이터만 전달하고, 도메인 객체 생성은 도메인이 한다** — 레이어 책임이 명확해진다

"하지 마세요"라고 문서에 쓰는 것보다, 아예 못하게 만드는 게 낫다.
