Redis 장애에도 진주문 생성을 보장하는 결제 설계


> **TL;DR**: 가주문은 Redis에 있다. PG 결제가 성공한 후 Redis가 죽으면 가주문을 꺼낼 수 없다. 하지만 TX-1에서 DB에 저장한 Payment 레코드에 진주문 생성에 필요한 정보가 전부 들어 있다. Redis는 빠른 경로이고, DB의 Payment는 확실한 경로다.

---

## 가주문이 Redis에 있는 이유

주문을 DB에 바로 INSERT하면, 결제 실패 시 DELETE하거나 상태를 롤백해야 한다. 결제 성공률이 42%인 환경에서 58%의 주문이 생성 후 삭제되는 셈이다.

가주문 패턴은 이 문제를 다르게 풀었다.

```
[기존]
주문서 작성 → DB INSERT (Order CREATED) → 결제 → 실패 → DELETE or CANCELLED

[가주문]
주문서 작성 → Redis SET (가주문, TTL 30분) → 결제 → 성공 → DB INSERT (진주문 PAID)
                                                  → 실패 → TTL 만료 → 자동 삭제
```

결제 실패 시 아무것도 안 해도 된다. TTL이 만료되면 Redis가 알아서 지운다. DB에는 성공한 주문만 남는다.

---

## 빈틈 ⑤ — PG 성공 후 Redis가 죽는다

콜백이 도착해서 진주문을 만들려고 한다. 가주문에서 상품 정보, 수량, 가격을 꺼내야 한다. 그런데 Redis가 죽어 있다.

```
Redis: HSET provisional:order:10042 {productId: 5, quantity: 2, amount: 50000}
PG: 결제 성공 (transactionKey: TX-abc123)
콜백 수신 → Redis 조회 시도 → Redis 장애 → 가주문 데이터 없음
→ 진주문 생성 불가?
```

---

## TX-1이 답이다

TX-1에서 Payment를 저장할 때, 결제에 필요한 정보를 같이 넣었다.

```java
Payment payment = Payment.create(
    orderId,       // 주문 ID
    productId,     // 상품 ID
    quantity,      // 수량
    amount,        // 금액
    REQUESTED      // 초기 상태
);
paymentRepository.save(payment);
```

콜백이 도착하면 transactionKey로 Payment를 찾는다.

```
콜백: transactionKey = TX-abc123
→ DB: SELECT * FROM payment WHERE transaction_key = 'TX-abc123'
→ Payment {orderId: 10042, productId: 5, quantity: 2, amount: 50000}
→ 진주문 생성에 필요한 정보가 전부 있다
```

Redis 가주문 없이도 Payment 레코드만으로 진주문을 만들 수 있다. Redis는 "빠른 경로"다. 결제 전에 주문 정보를 빠르게 읽고 쓰기 위한 것이지, 유일한 저장소가 아니다.

---

## Redis 장애 시 Fallback

Redis 장애는 가주문 조회뿐 아니라 가주문 생성 단계에서도 발생할 수 있다.

```java
@CircuitBreaker(name = "redis-write", fallbackMethod = "saveToDbFallback")
public ProvisionalOrderResult saveProvisionalOrder(OrderCreateRequest request) {
    // Redis 정상: 가주문 + 재고 예약
    masterRedisTemplate.opsForHash().putAll(key, orderData);
    masterRedisTemplate.opsForValue().decrement("stock:" + productId);
    return ProvisionalOrderResult.provisional(orderId);
}

public ProvisionalOrderResult saveToDbFallback(
        OrderCreateRequest request, Exception e) {
    // Redis 장애: DB 직접 주문
    Order order = Order.create(request);
    orderRepository.save(order);
    productRepository.decreaseStock(request.productId(), request.quantity());
    return ProvisionalOrderResult.directOrder(order.getId());
}
```

Redis CB가 Open이면 가주문을 건너뛰고 DB에 직접 주문을 생성한다. 가주문 패턴의 이점(결제 실패 시 자동 정리)은 잃지만, 결제 자체는 계속 진행된다.

이 CB는 쓰기 전용이다. 읽기(가주문 조회)에는 CB를 걸지 않았다 — 시리즈 1편에서 다룬 이유와 같다. 읽기를 차단하면 복구가 멈춘다.

---

## Redis DEL 실패도 허용한다

진주문 전환 후 Redis 가주문을 삭제한다. 이 삭제가 실패해도 문제없다.

```java
// 진주문 전환 완료 후
try {
    provisionalOrderRedisRepository.deleteByOrderId(orderId);
} catch (Exception e) {
    log.warn("가주문 삭제 실패 (허용): orderId={}", orderId);
    // 예외를 던지지 않는다
}
```

가주문에 TTL이 걸려 있으므로 삭제가 실패해도 25~35분 후에 자동으로 사라진다. 그 전에 Proactive Expiry Scanner(30초 주기)가 TTL이 임박한 가주문을 선제 정리하면서 재고도 복원한다.

```
Redis DEL 성공 → 즉시 정리
Redis DEL 실패 → TTL 만료 → 자동 삭제
                   or Proactive Expiry Scanner → 선제 정리 + 재고 복원
```

---

## TTL Jitter

가주문 TTL은 30분인데, 정확히 30분으로 설정하지 않았다.

```java
private Duration calculateTtlWithJitter() {
    long jitter = ThreadLocalRandom.current().nextLong(-300, 301);
    return Duration.ofSeconds(1800 + jitter);  // 25분 ~ 35분
}
```

플래시 세일로 1000건이 동시에 생성되면, TTL이 동일할 경우 30분 후에 1000건이 동시에 만료된다. Proactive Expiry Scanner 한 번에 1000건을 처리하면 600ms가 걸린다. Jitter를 ±5분 주면 만료가 10분에 걸쳐 분산되어 한 번에 25ms 수준으로 줄어든다.

---

## 닻의 역할 정리

TX-1 커밋 시점에 DB에 들어가는 Payment 레코드가 다섯 개의 빈틈 전체에서 기준점 역할을 한다. 이 글에서 다룬 빈틈 ⑤만이 아니다.

| 빈틈 | Payment가 제공하는 것 |
|------|----------------------|
| ① PG 미호출 | Outbox와 함께 저장됨 → Poller가 재호출 |
| ② DB 저장 실패 | orderId → WAL과 매핑 |
| ③ 콜백 유실 | transactionKey → Polling 조회 키 |
| ④ 콜백 처리 실패 | Payment 레코드 → 재처리 대상 |
| ⑤ Redis 장애 | orderId, productId, amount → 진주문 생성 정보 |

TX-1이 커밋되면 Payment는 DB에 있다. DB에 있으면 유실되지 않는다. Redis가 죽어도, 콜백이 유실되어도, 서버가 재시작되어도, 이 레코드를 기준으로 복구할 수 있다.

---

## 돌아보며

Redis를 도입하면 장애 포인트가 하나 늘어난다. "장애 포인트가 늘어나니 안 쓰는 게 낫다"는 판단도 가능하다.

이번에는 다르게 접근했다. Redis가 죽는 상황을 설계에 포함시키고, 죽었을 때 DB가 대신하는 경로를 만들었다. 가주문 생성은 DB Fallback으로, 가주문 조회는 Payment 레코드로, 가주문 삭제는 TTL 만료로. 세 가지 Redis 장애 시나리오 각각에 대체 경로가 있다.

Redis가 정상이면 빠르고, 죽어도 느릴 뿐 멈추지 않는다. 이 정도면 장애 포인트를 추가한 대가로 충분하다고 판단했다.
