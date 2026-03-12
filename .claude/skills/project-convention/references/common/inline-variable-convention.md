# 인라인 변수 컨벤션 (Inline Variable Convention)

> 일회용 지역변수를 제거하고, 객체 생성과 사용을 한 흐름으로 응집시킨다. 모든 레이어(Controller, Facade, Service, Domain)에 적용한다.

---

## 핵심 원칙

### 1. 일회용 지역변수는 인라인한다

변수가 **생성 직후 단 한 번만 참조**되면 인라인한다.

```java
// Bad — 일회용 변수가 코드만 늘린다
OrderCriteria.ListByDate criteria = new OrderCriteria.ListByDate(startAt, endAt);
List<OrderResult.OrderSummary> results = orderFacade.getMyOrders(loginUser.id(), criteria);
OrderResponse.ListResponse listResponse = new OrderResponse.ListResponse(
    results.stream().map(OrderResponse.OrderSummary::from).toList());
return ApiResponse.success(listResponse);

// Good — 흐름이 한눈에 읽힌다
List<OrderResult.OrderSummary> results =
        orderFacade.getMyOrders(
                loginUser.id(),
                new OrderCriteria.ListByDate(startAt, endAt));

return ApiResponse.success(
        new OrderResponse.ListResponse(
                results.stream().map(OrderResponse.OrderSummary::from).toList()));
```

### 2. 객체 생성과 사용을 한 표현식으로 작성한다

```java
// Bad — 생성과 사용이 분리되어 응집도 저하
OrderItem orderItem = new OrderItem(product, quantity);
order.add(orderItem);

// Good — 생성과 사용이 한 표현식
order.add(new OrderItem(product, quantity));
```

### 3. 변수를 유지하는 경우

다음 조건 중 하나라도 해당하면 변수로 추출한다.

| 조건 | 이유 | 예시 |
|------|------|------|
| **2회 이상 참조** | 중복 호출 방지 | `results`를 응답 변환 + 로깅에 사용 |
| **의미 경계가 달라지는 지점** | 가독성, 디버깅 용이 | Facade/Service 호출 결과 |
| **인라인 시 한 줄이 3단계 이상 중첩** | 가독성 한계 | 아래 예시 참고 |

```java
// 과도한 인라인 — 중첩이 너무 깊다
return ApiResponse.success(
        new OrderResponse.DetailResponse(
                OrderResponse.OrderDetail.from(
                        orderFacade.getOrderDetail(
                                loginUser.id(),
                                new OrderCriteria.Detail(orderId)))));

// 의미 경계에서 끊는다
OrderResult.OrderDetail result =
        orderFacade.getOrderDetail(
                loginUser.id(),
                new OrderCriteria.Detail(orderId));

return ApiResponse.success(
        new OrderResponse.DetailResponse(
                OrderResponse.OrderDetail.from(result)));
```

---

## 줄바꿈 & 들여쓰기 스타일

### Chop-down 스타일 (권장)

메서드 인자가 한 줄에 안 들어가면, **첫 번째 인자부터 줄바꿈** + **8칸(continuation indent)** 적용한다.

```java
// 한 줄에 들어가면 그대로
order.add(new OrderItem(product, quantity));

// 안 들어가면 chop-down
List<OrderResult.OrderSummary> results =
        orderFacade.getMyOrders(
                loginUser.id(),
                new OrderCriteria.ListByDate(startAt, endAt));
```

괄호 정렬 방식은 메서드명 길이에 따라 들여쓰기가 변동되어 diff가 지저분해지므로, 고정 8칸 indent를 사용한다.

### 닫는 괄호 위치

연쇄된 닫는 괄호 `))` 는 마지막 인자 뒤에 붙인다.

```java
// Good — 닫는 괄호는 마지막 인자에 붙인다
return ApiResponse.success(
        new OrderResponse.ListResponse(
                results.stream().map(OrderResponse.OrderSummary::from).toList()));

// Bad — 닫는 괄호를 별도 줄에 내린다
return ApiResponse.success(
        new OrderResponse.ListResponse(
                results.stream().map(OrderResponse.OrderSummary::from).toList()
        )
);
```

---

## 레이어별 적용 예시

### Controller

```java
@GetMapping
@Override
public ApiResponse<OrderResponse.ListResponse> list(
        @Login LoginUser loginUser,
        @RequestParam ZonedDateTime startAt,
        @RequestParam ZonedDateTime endAt
) {
    List<OrderResult.OrderSummary> results =
            orderFacade.getMyOrders(
                    loginUser.id(),
                    new OrderCriteria.ListByDate(startAt, endAt));

    return ApiResponse.success(
            new OrderResponse.ListResponse(
                    results.stream().map(OrderResponse.OrderSummary::from).toList()));
}
```

---

## 판단 기준

- 변수가 2회 이상 참조되면 변수 유지
- 1회 참조이나 인라인 시 3단계 이상 중첩되면 의미 경계에서 변수 추출
- 그 외 1회 참조 변수는 인라인한다
