---
name: ddd-dev-guidelines
description: "DDD 도메인 모델링, Entity/VO/Domain Service 구분, 레이어드 아키텍처 + DIP, 유스케이스 기반 객체 협력 설계. 도메인 코드를 구현할 때 활성화한다."
---

도메인 모델링과 아키텍처를 설계/구현할 때 반드시 아래 원칙을 따른다.

## 도메인 모델링 원칙

도메인 모델링은 현실 세계의 개념과 규칙을 객체 지향적으로 표현하는 작업이다.
핵심은 **데이터가 아니라 행위의 주체와 책임**이다.

### Entity
- **식별 가능**한 고유 ID를 가지며, **상태 변화**가 중요하다
- 동일성은 ID로 판단. 시간이 지나 속성이 동일하더라도 연속성을 가진다
- 도메인 규칙을 내부에서 스스로 수행한다 (비즈니스 로직 캡슐화)

```java
public class User extends BaseEntity {
    private Money balance;

    public boolean canAfford(Money amount) {
        return this.balance.isGreaterThanOrEqual(amount);
    }

    public void pay(Money amount) {
        if (!canAfford(amount)) {
            throw new CoreException(OrderErrorType.INSUFFICIENT_BALANCE, "포인트가 부족합니다.");
        }
        this.balance = this.balance.minus(amount);
    }
}
```

### Value Object (VO)
- **값 동등성**: "누구인지"가 아니라 "그 값이 무엇이냐"만 중요
- **불변(immutable)**: 한 번 생성되면 변경 불가
- **자체 검증**: 생성자에서 유효성 검증 수행

```java
// record로 불변 VO 구현
public record Money(BigDecimal amount) {
    public Money {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
    }

    public Money plus(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    public Money minus(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    public boolean isGreaterThanOrEqual(Money other) {
        return this.amount.compareTo(other.amount) >= 0;
    }
}
```

**VO vs Entity 판단 기준:**
- ID가 필요하고 상태 추적이 필요하면 → Entity
- 값 자체가 동일하면 같은 것으로 간주 → VO
- 컨텍스트에 따라 달라질 수 있다 (주소: 배송에선 VO, 우체국에선 Entity)

### Domain Service
- **상태를 갖지 않는다** (input → output이 명확)
- 도메인 객체들이 직접 수행하기 어려운 도메인 로직을 위임받아 처리
- Manager/Doer 패턴 지양: "행위자" 객체는 도메인이 아닌 서비스

```java
public class PointChargingService {
    public void charge(User user, Money amount) {
        if (amount.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("0원 이상만 충전할 수 있습니다.");
        }
        user.receive(amount);
    }
}
```

**도메인 서비스 도입 기준:**
- 규칙이 여러 서비스에 나타나면 → 도메인 객체에 속할 가능성이 높다
- 여러 도메인 객체 협력이 필요하면 → 도메인 서비스로 분리
- Entity에 넣기 어색한 비즈니스 로직 → 도메인 서비스

## 도메인 간 분리 기준

비즈니스 의미가 커질 수 있는 개념은 독립 도메인으로 격리한다.

```java
// Bad: 좋아요를 Product에 내장
class Product {
    private Set<Long> likedUserIds; // 확장 어려움
}

// Good: 좋아요를 독립 도메인으로 분리
class Like {
    private final Long userId;
    private final Long productId;
    private final LocalDateTime likedAt;
}
```

## 레이어드 아키텍처 + DIP

```
[ Interfaces Layer ]     /interfaces/api/xx     - 사용자와 직접 연결, HTTP 관심사
        ↓
[ Application Layer ]    /application/xx        - 유스케이스 실행, 흐름 조율
        ↓
[ Domain Layer ]         /domain/xx             - 비즈니스 로직의 핵심
        ↑
[ Infrastructure Layer ] /infrastructure/xx     - 외부 기술 의존 (JPA, Redis 등)
```

### 계층별 책임

**Interfaces 계층**
- Application Layer의 유스케이스 호출 책임만 갖는다
- 요청 객체 검증, 응답 객체 매핑 수행

**Application 계층**
- 각 비즈니스 기능의 흐름을 조율해 유스케이스를 완성
- 실질적인 비즈니스 로직은 최대한 도메인으로 위임
- 여러 도메인 서비스를 조합할 때 Facade 사용

**Domain 계층**
- 도메인 로직은 도메인 계층에 위치하며, 다른 계층에 의존하지 않는다
- 비즈니스의 중심이며, 모든 의존 방향은 도메인 계층을 향한다
- Entity, VO, Domain Service, Repository 인터페이스가 위치

**Infrastructure 계층**
- 도메인이 원하는 기능을 구체적인 외부 기술로 제공
- Repository 인터페이스의 구현체가 위치

### DIP (Dependency Inversion Principle)

의존성 방향을 Domain -> Infra가 아닌, Domain(인터페이스) <- Infra(구현체)로 뒤집는다.

```java
// Domain Layer: 인터페이스 (포트)
public interface OrderRepository {
    Order save(Order order);
    Optional<Order> findById(Long id);
}

// Infrastructure Layer: 구현체 (어댑터)
@Repository
public class OrderRepositoryImpl implements OrderRepository {
    private final OrderJpaRepository orderJpaRepository;

    public OrderRepositoryImpl(OrderJpaRepository orderJpaRepository) {
        this.orderJpaRepository = orderJpaRepository;
    }

    @Override
    public Order save(Order order) {
        return this.orderJpaRepository.save(order);
    }
}
```

**DIP의 이점:**
- 테스트 시 FakeRepository, InMemoryRepository로 대체 가능
- DB 교체, 비즈니스 로직 변경에도 영향 최소화
- 도메인이 외부 기술에 의존하지 않음

## 유스케이스 중심 객체 협력

복잡한 협력은 반드시 테스트 가능한 구조로 끊어서 설계한다.

### 주문 생성 유스케이스 예시

| 객체 | 역할 |
|------|------|
| `Order` | 주문 항목 집합, 총 금액 계산, 생성 책임 |
| `Product` | 재고 확인 및 감소 책임 |
| `User` | 포인트 보유 및 차감 책임 |
| `OrderService` | 이들을 조합하여 유스케이스 단위로 처리 |

```java
public class OrderService {
    private final OrderRepository orderRepository;

    public Order createOrder(User user, List<Pair<Product, Integer>> products) {
        Money totalPrice = calculateTotalPrice(products);

        // 도메인 객체에게 비즈니스 로직 위임
        products.forEach(p -> p.getFirst().decreaseStock(p.getSecond()));
        user.pay(totalPrice);

        Order order = Order.create(user.getId(), toOrderItems(products));
        return this.orderRepository.save(order);
    }
}
```

### 설계 체크리스트

- [ ] 핵심 비즈니스 로직이 Entity, VO, Domain Service에 위치하는가?
- [ ] Application Layer는 도메인 객체를 조합해 흐름을 orchestration하는가?
- [ ] Repository Interface는 Domain Layer에 정의되고, 구현체는 Infra에 위치하는가?
- [ ] 패키지는 계층 + 도메인 기준으로 구성되었는가?
- [ ] 도메인 객체가 다른 계층에 의존하지 않는가?
- [ ] 재고 음수 방지 등 도메인 규칙이 도메인 레벨에서 처리되는가?