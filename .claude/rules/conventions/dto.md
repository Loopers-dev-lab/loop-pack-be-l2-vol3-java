# DTO

## DTO 종류와 소속 계층

| DTO | 소속 | 역할 | 형태 |
|-----|------|------|------|
| `Request` | application | Facade 입력 (명령 + 조회) | `Place`, `Cancel`, `ListByUser` 등 |
| `Command` | application | Service 입력 (비즈니스 보강) | `Create`, `Update` 등 |
| `Info` | application | Facade 출력 → Controller | 도메인 데이터의 읽기 전용 표현 |
| `V1Dto` | interfaces | HTTP 응답 전용 | `XxxResponse` |

- 모든 DTO는 Java record로 정의한다 (불변 보장)
- Entity는 application 계층 밖으로 노출하지 않는다

## 데이터 흐름
```
Controller             Facade                Service
Request.Place    →  @Valid Request.Place  →
                    Command.Create       →  Command.Create  →  Entity
                    Info                 ←  Entity          ←
V1Dto.Response   ←  Info
```

1. **Controller → Facade**: `Request`를 `@RequestBody`로 직접 받아 Facade에 전달
2. **Facade → Service**: `Request`를 DB 조회 등으로 보강하여 `Command`로 재조립하여 전달
3. **Service → Facade**: Entity 반환
4. **Facade → Controller**: Entity를 `Info`로 변환하여 반환
5. **Controller → 클라이언트**: `Info`를 `V1Dto.Response`로 변환

## Request 구조

Request는 도메인별로 하나의 클래스에 중첩 record로 정의한다.
Controller에서 `@RequestBody`로 직접 받으며, Facade에서 `@Validated`로 검증한다.
```java
public record OrderRequest() {

    // Command
    public record Place(
            @NotNull @Size(min = 1, max = 100)
            List<@Valid PlaceItem> orderItems
    ) {}
    public record PlaceItem(
            @NotNull Long productId,
            @NotNull @Min(1) @Max(9999999) Integer quantity
    ) {}
    public record Cancel(String cancelReason) {}

    // Query
    public record ListByUser(LocalDate startDate, LocalDate endDate, Integer page, Integer size) {}
}
```

| 구분 | 용도 | 데이터 특성 |
|------|------|-------------|
| `// Command` | 명령 요청 (POST/PATCH/DELETE) | 사용자 입력값 (ID, 수량 등) |
| `// Query` | 조회 파라미터 (GET) | 필터, 페이징 등 |

## Command 구조

Command는 도메인별로 하나의 클래스에 중첩 record로 정의한다.
Facade가 Request를 DB 조회 결과 등으로 보강하여 생성한다.
```java
public record OrderCommand() {
    public record Create(Long userId, List<CreateItem> items) {}
    public record CreateItem(Long productId, String productName, BigDecimal price, int quantity) {}
    public record Cancel(Long userId, Long orderId, String cancelReason) {}
}
```

| 구분 | 용도 | 데이터 특성 |
|------|------|-------------|
| `Create`, `Update` 등 | Facade → Service | 비즈니스 데이터 포함 (이름, 가격 등) |

## Info 구조

Info는 도메인별로 하나의 record에 중첩 record로 정의한다.
```java
public record OrderInfo(
        Long id,
        BigDecimal totalAmount,
        List<OrderItemInfo> orderItems,
        ZonedDateTime createdAt
) {
    public record OrderItemInfo(...) {}

    public static OrderInfo from(Order order) { ... }
}
```

- `from(Entity)` 정적 팩토리 메서드로 Entity → Info 변환
- Facade에서 변환 책임을 가진다

## V1Dto 구조

V1Dto는 Response 전용이다. Request는 application 계층의 `Request`를 사용한다.
```java
public class OrderV1Dto {

    // Response
    public record OrderResponse(...) {
        public static OrderResponse from(OrderInfo info) { ... }
    }
}
```