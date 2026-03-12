# DTO 컨벤션

## 계층별 DTO 네이밍

| 계층 | 요청 (입력) | 응답 (출력) | 비고 |
|------|------------|------------|------|
| **Interface** | `~Request` | `~Response` | API 스펙 종속, `@Valid` 부착 |
| **Application** | `~Criteria` | `~Result` | 유스케이스 단위 입출력. Criteria는 Domain의 Command를 참조/조합 가능 |
| **Domain Service** | `~Command` | **Entity** 또는 `~Info` | 자기 도메인 비즈니스 입력 (타 도메인 정보 명세 포함) |

- Domain Service는 **Entity를 직접 반환하는 게 기본**. `Info`는 Entity 하나로 표현이 안 될 때만 생성한다.
- Application `~Criteria`는 유스케이스 입력을 표현하며, 내부에서 Domain Service의 `~Command`를 참조하거나 조합할 수 있다.
- Application `~Result`는 유스케이스 결과를 표현한다 (단일/조합 구분 없이 통합).
- Domain Service `~Command`는 자기 도메인 비즈니스 명령과 타 도메인 정보 명세를 모두 포함한다.
- Domain 계층 자체(Entity)는 DTO를 사용하지 않는다.

---

## DTO 작성 규칙

### 1. Inner Class + Record 활용

DTO는 **record**로 작성하고, 관련 DTO끼리 **Inner Class**로 그룹핑한다.

```java
// Interface 계층 — {Domain}Dto 그룹 클래스
public class ProductDto {
    public record CreateRequest(
        @NotBlank String name,
        @Positive int price
    ) {
        public ProductCriteria.Create toCreateCriteria() {
            return new ProductCriteria.Create(name, price);
        }
    }
    public record DetailResponse(Long id, String name, int price) {
        public static DetailResponse from(ProductResult result) {
            return new DetailResponse(result.id(), result.name(), result.price());
        }
    }
}

// Application 계층 — {Domain}Criteria 그룹 클래스
public class ProductCriteria {
    public record Create(String name, int price) {
        public ProductCommand.Create toCommand() {
            return new ProductCommand.Create(name, price);
        }
    }
}

// Application 계층 — {Domain}Result (Top-level record)
public record ProductResult(Long id, String name, int price) {
    public static ProductResult from(Product entity) {
        return new ProductResult(entity.getId(), entity.getName(), entity.getPrice());
    }
}

// Domain 계층 — {Domain}Command 그룹 클래스 (domain/{도메인}/dto/)
public class ProductCommand {
    public record Create(String name, int price) {}
    public record Update(Long id, String name, int price) {}
    public record StockDeduction(Long productId, int quantity, int expectedPrice) {}
}

// Domain 계층 — {Domain}Info 그룹 클래스 (domain/{도메인}/dto/)
// Entity 하나로 표현 불가능할 때만 생성
public class ProductInfo {
    public record StockDeduction(Long productId, String name, int price, int quantity, Long brandId) {}
}
```

### 2. 변환 메서드 위치: "아는 쪽"에 둔다

의존 방향(상위 → 하위)을 지키며, **변환 대상을 아는 쪽**에 메서드를 배치한다.

| 변환 | 메서드 위치 | 형태 | 예시 |
|------|-----------|------|------|
| Request → Criteria | Request | `toCriteria()` | `request.toCreateCriteria()` |
| Criteria → Command | Criteria | `toCommand()` | `criteria.toCommand()` |
| Entity → Result | Result | `static from()` | `ProductResult.from(entity)` |
| Result → Response | Response | `static from()` | `DetailResponse.from(result)` |
| Entity/Info → Command (타 도메인) | Command | `static from()` | `OrderProductCommand.from(product)` |

- 하위 계층은 상위 계층 DTO를 알지 않는다 (Domain → Application DTO 참조 금지, Application → Interface DTO 참조 금지).

### 3. 타 도메인 Command 패턴

```java
// 주문 도메인이 상품 도메인에 요구하는 정보 명세
public record OrderProductCommand(Long productId, String name, int price, Long shopId) {
    public static OrderProductCommand from(ProductResult product) {
        return new OrderProductCommand(
            product.id(), product.name(), product.price(), product.shopId());
    }
}

// Domain Service — 기본: Entity 직접 반환
public Order create(OrderMemberCommand member, List<OrderProductCommand> products) {
    return Order.create(member.memberId(), products);
}
```

---

## 파라미터 전달 규칙

### 1. Application → Domain Service 입력

| 파라미터 수 | 전달 방식 | 예시 |
|------------|----------|------|
| **1~3개** | 원시 타입 직접 전달 | `orderService.create(memberId, address, shopId)` |
| **4개 이상** | DTO(`~Command`) 사용 | `orderService.create(orderProductCommand)` |

```java
// 파라미터 3개 이하 → 원시 타입
public Order create(Long memberId, String address, Long shopId) { ... }

// 파라미터 4개 이상 → Command DTO
public Order create(OrderProductCommand productCommand) { ... }
```

### 2. Domain Service에는 타 도메인 정보를 Command DTO로 전달

Domain Service의 메서드 시그니처에 타 도메인의 Entity/VO 대신 **Command DTO**를 사용한다.

```java
public class OrderService {
    public Order create(OrderMemberCommand member, List<OrderProductCommand> products) { ... }
}
```

Application 계층(Facade)이 타 도메인 Entity/Result → Command 변환을 책임진다.

### 3. 타 도메인 출력 조합: Application에서 `~Result`

여러 도메인의 출력을 합쳐야 할 때, **Application 계층이 `~Result`로 조합**한다.

```java
public record OrderDetailResult(
    OrderInfo order,
    ProductInfo product,
    MemberInfo member
) {}
```

### Domain Service 응답 기준

| 상황 | Domain Service 응답 | 사용 시점 |
|------|-------------------|----------|
| Entity로 충분 | **Entity 직접 반환** | 대부분의 경우 |
| Entity로 표현 불가 | `~Info` | 복합 결과가 필요할 때 |

---

## 계층 간 흐름 요약

```
Client
  → ProductCreateRequest              (Interface 입력)
    → ProductCriteria.Create           (Application 입력 — Command 참조 가능)
      → ProductCommand.Create          (Domain 입력)
      ← Entity 또는 ~Info              (Domain 출력)
    ← ProductResult                    (Application 출력)
  ← ProductCreateResponse             (Interface 출력)
```
