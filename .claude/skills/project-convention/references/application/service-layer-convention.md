# 서비스 계층 책임 분리 컨벤션

## 1. 계층 구조 개요

> 계층 다이어그램은 SKILL.md "아키텍처" 섹션 참조.

| 계층 | 클래스 | 역할 |
|------|--------|------|
| **Application** | `{Domain}Facade` | 유스케이스 조율, 도메인 간 흐름 조합, DTO 변환 |
| **Domain** | `{Domain}Service` | 자기 도메인 비즈니스 로직, Repository 접근, Entity 조작 |

Application 계층에는 **Facade만 둔다**. 별도 ApplicationService 개념을 두지 않는다.

---

## 2. Facade — Application 계층

### Facade 역할 경계

Facade가 담당하는 것:

- 여러 Domain Service를 호출하여 하나의 비즈니스 흐름을 완성 (유스케이스 조율)
- Entity → Result 변환, 여러 Info → Result 조합 (DTO 변환)
- 타 도메인 Result/Info → 자기 도메인 Command 변환 후 Service에 전달
- 여러 Domain Service 호출의 원자성 보장 (트랜잭션 경계)
- Domain이 제공하는 계산 메서드를 **호출만** 한다 (`stream().mapToInt().sum()` 등 계산/집계 로직은 Entity 또는 Entity 정적 메서드에 배치)

Facade가 아닌 곳에 배치하는 것:

- 비즈니스 규칙, 검증 로직 → Domain Service 또는 Entity
- Repository 직접 호출 → Domain Service
- Entity 상태 변경 로직 → Entity 메서드
- **private 메서드를 생성하지 않는다** — 필요하다면 해당 로직은 Domain Service 또는 Entity에 속해야 한다는 신호

### 일괄 조회 원칙 (N+1 방지)

Facade에서 여러 엔티티를 다룰 때 **IN절 일괄 조회**를 사용한다. 루프 안에서 개별 조회를 반복하지 않는다.

```java
// ID 목록 추출 → IN절 일괄 조회 → Map으로 매핑
List<Long> productIds = criteria.items().stream()
    .map(CreateItem::productId)
    .toList();

Map<Long, ProductModel> productMap = productService.getAllByIds(productIds).stream()
    .collect(Collectors.toMap(ProductModel::getId, Function.identity()));

criteria.items().stream()
    .map(item -> {
        ProductModel product = productMap.get(item.productId());
        ...
    });
```

- Domain Service에 `getAllByIds(List<Long>)` 메서드를 제공하고, 조회 결과 개수 검증은 Service가 담당한다

### 예시

```java
@Service
public class OrderFacade {

    private final OrderService orderService;
    private final ProductService productService;
    private final MemberService memberService;

    @Transactional
    public OrderResult createOrder(OrderCriteria.Create criteria) {
        // 1. 타 도메인에서 필요한 데이터 조회
        MemberInfo member = memberService.getMember(criteria.memberId());
        List<ProductInfo> products = productService.getProducts(criteria.productIds());

        // 2. 타 도메인 Info → 자기 도메인 Command 변환
        OrderMemberCommand memberCommand = OrderMemberCommand.from(member);
        List<OrderProductCommand> productCommands = products.stream()
            .map(OrderProductCommand::from)
            .toList();

        // 3. 자기 도메인 Service에 위임
        Order order = orderService.create(memberCommand, productCommands);

        // 4. Entity → Result 변환 후 반환
        return OrderResult.from(order);
    }

    @Transactional(readOnly = true)
    public OrderDetailResult getOrderDetail(OrderCriteria.Detail criteria) {
        OrderInfo order = orderService.getOrderInfo(criteria.orderId());
        ProductInfo product = productService.getProduct(order.productId());
        MemberInfo member = memberService.getMember(order.memberId());

        return new OrderDetailResult(order, product, member);
    }
}
```

---

## 3. Domain Service — Domain 계층

### Domain Service 역할 범위

- Repository를 통한 Entity 조회/저장
- Entity 생성 → 저장 흐름
- Entity 자기 필드만으로 완결되지 않는 비즈니스 규칙 (Repository 조회 필요, 여러 Entity 조율)
- 같은 도메인 내 여러 Entity 조율

Domain Service 외부에 배치하는 것:

- 타 도메인 조율 → Facade에서 처리
- DTO 변환 (Info → Response 등) → Facade 또는 Interface 계층
- Entity 자기 필드만으로 완결되는 로직 → Entity 메서드

### CUD 메서드의 반환 규칙

CUD 메서드는 **기본적으로 void** 반환. Entity 반환이 허용되는 경우:
- Facade에서 생성된 Entity의 **ID나 상태를 즉시 사용**해야 할 때
- **별도 조회가 비효율적**일 때 (save() 직후 동일 Entity를 다시 findById()하는 것은 불필요)

```java
// Entity 반환 — 생성 후 ID/상태를 Facade에서 즉시 사용
@Transactional
public Order create(OrderMemberCommand member, List<OrderProductCommand> products) {
    Order order = Order.create(member.memberId(), products);
    return orderRepository.save(order);
}

// void — 상태 변경 후 반환할 필요 없음
@Transactional
public void cancel(Long orderId) {
    Order order = getOrder(orderId);
    order.cancel();
}
```

### 예시

```java
@Service
public class OrderService {

    private final OrderRepository orderRepository;

    @Transactional
    public Order create(OrderMemberCommand member, List<OrderProductCommand> products) {
        List<OrderLine> lines = products.stream()
            .map(p -> OrderLine.create(p.productId(), p.name(), p.price()))
            .toList();
        Order order = Order.create(member.memberId(), lines);
        return orderRepository.save(order);
    }

    @Transactional
    public void cancel(Long orderId) {
        Order order = getOrder(orderId);
        order.cancel();  // Entity에 위임
    }

    @Transactional(readOnly = true)
    public Order getOrder(Long orderId) {
        return orderRepository.findById(orderId)
            .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public OrderInfo getOrderInfo(Long orderId) {
        return OrderInfo.from(getOrder(orderId));
    }
}
```

---

## 4. 트랜잭션 규칙

- **메서드 레벨**에 `@Transactional`을 붙인다 (클래스 레벨 금지)
- 조회 전용 메서드에는 `@Transactional(readOnly = true)` 필수
- Facade + Domain Service **양쪽 모두** `@Transactional` 부착 — Spring 기본 전파(`REQUIRED`)로 Facade가 시작한 트랜잭션에 Domain Service가 참여하고, 단독 호출 시에는 자체 트랜잭션 시작
- **readOnly 전파 주의**: Facade가 `readOnly = true`이면 하위 Service도 readOnly로 전파되므로, 조회 Facade에서 변경 Service를 호출하지 않는다

---

## 5. 계층 간 호출 규칙

| 호출 방향 | 허용 | 비고 |
|-----------|:----:|------|
| Controller → Facade | O | |
| Facade → 자기 도메인 Service | O | |
| Facade → 타 도메인 Service | O | |
| Facade → Entity getter (재료 추출) | O | 인자 준비, DTO 변환용 |
| Domain Service → 자기 도메인 Repository | O | |
| Entity → Entity (같은 Aggregate) | O | |
| Facade → Entity 상태 변경/정적 팩토리 직접 호출 | X | Domain Service를 통해 호출 |
| Facade → 타 Facade | X | 순환 의존, 트랜잭션 경계 혼란 |
| Domain Service → 타 도메인 Service/Repository | X | 도메인 간 결합 |
| Domain Service → Facade | X | 하위 → 상위 역방향 |
| Controller → Domain Service 직접 | X | Facade 우회 |

### Facade가 타 도메인에 접근하는 방법

Facade는 타 도메인의 **Domain Service**를 직접 주입받아 호출한다.

```java
@Service
public class OrderFacade {
    private final OrderService orderService;      // 자기 도메인
    private final ProductService productService;  // 타 도메인
    private final MemberService memberService;    // 타 도메인
}
```

---
