# Decomposition 패턴으로 이커머스 프로젝트 점검하기

> microservices.io의 Decomposition 패턴 4가지로 현재 프로젝트를 점검하고, 도출된 개선점을 실제로 적용한 기록.

---

## 1. 점검에 사용한 4가지 패턴

| 패턴 | 핵심 질문 |
|------|----------|
| **Decompose by Business Capability** | 도메인별로 책임이 분리되어 있는가? |
| **Decompose by Subdomain** | Bounded Context 경계가 코드에 반영되어 있는가? |
| **Self-Contained Service** | 각 서비스가 동기 의존 없이 독립 동작 가능한가? |
| **Service per Team** | 팀 단위로 독립 배포·운영이 가능한 구조인가? |

---

## 2. 점검 결과

### 잘 되어 있는 부분

- **패키지 구조**: `application/{domain}/`, `domain/{domain}/` 패턴으로 Business Capability별 분리 완료.
- **Facade 패턴**: 유스케이스 조율이 Application Layer에서 이루어지고, 도메인 로직은 Entity/VO에 캡슐화.
- **Aggregate 간 ID 참조**: Order → Product, Order → CouponIssue 등 느슨한 결합 유지.

### 개선이 필요한 부분

1. **EventOutbox 보일러플레이트**: LikeFacade, OrderFacade가 각각 `EventOutboxRepository` + `ObjectMapper` + `ApplicationEventPublisher` 3개를 직접 조합. Outbox 저장 + 이벤트 발행 패턴이 중복.
2. **PaymentRecoveryService cross-domain 접근**: 결제 복구 서비스가 `ProductRepository`, `StockReservationRedisRepository`, `CouponIssueRepository`를 직접 사용. Product/Coupon 도메인의 내부 구현에 결합.
3. **Self-Contained Service 관점**: 결제 도메인이 상품 재고의 Redis + DB 이중 쓰기 패턴을 알고 있는 것은 도메인 경계 위반.

---

## 3. 개선 1: DomainEventPublisher 추상화

### Before

```java
// LikeFacade — 3개 인프라 의존
private final EventOutboxRepository eventOutboxRepository;
private final ApplicationEventPublisher applicationEventPublisher;
private final ObjectMapper objectMapper;

// Outbox + Event 발행 로직이 Facade에 직접 존재
EventOutbox outbox = EventOutbox.create("catalog", productId, "LIKE_CREATED", buildPayload(...));
eventOutboxRepository.save(outbox);
applicationEventPublisher.publishEvent(new LikeCreatedEvent(...));
```

### After

```java
// LikeFacade — 1개 도메인 인터페이스 의존
private final DomainEventPublisher domainEventPublisher;

// 한 줄로 완결
domainEventPublisher.publish("catalog", productId, "LIKE_CREATED",
    Map.of("productId", productId, "memberId", memberId),
    new LikeCreatedEvent(productId, memberId));
```

### 설계 포인트

- **DomainEventPublisher 인터페이스**는 `domain/event/`에 위치 → Facade가 인프라에 의존하지 않음.
- **DomainEventPublisherImpl**은 `infrastructure/event/`에 위치 → Outbox 저장 + Spring Event 발행을 캡슐화.
- 마이크로서비스 분리 시 구현체만 교체 (Outbox → Kafka 직접 발행)하면 Facade 코드 변경 없음.

---

## 4. 개선 2: PaymentRecoveryService cross-domain 위임

### Before

```java
// PaymentRecoveryService — Product/Coupon 도메인 직접 접근
private final ProductRepository productRepository;
private final CouponIssueRepository couponIssueRepository;
private final StockReservationRedisRepository stockRedisRepository;

private void handlePaymentFailure(PaymentModel payment) {
    // Redis INCR + DB increaseStock 이중 쓰기를 직접 수행
    stockRedisRepository.increase(item.getProductId(), item.getQuantity());
    productRepository.findById(item.getProductId()).ifPresent(product -> {
        product.increaseStock(item.getQuantity());
        productRepository.save(product);
    });
    // 쿠폰 내부 상태 직접 조작
    couponIssueRepository.findById(couponIssueId).ifPresent(couponIssue -> {
        couponIssue.cancelUse(ZonedDateTime.now());
    });
}
```

### After

```java
// PaymentRecoveryService — Facade 위임
private final ProductFacade productFacade;
private final CouponFacade couponFacade;

private void handlePaymentFailure(PaymentModel payment) {
    for (OrderItem item : order.getItems()) {
        productFacade.restoreStock(item.getProductId(), item.getQuantity());
    }
    if (order.getCouponIssueId() != null) {
        couponFacade.restoreCoupon(order.getCouponIssueId());
    }
}
```

### 설계 포인트

- **재고 복원 로직(Redis + DB)은 ProductFacade가 소유**: Product 도메인의 내부 구현을 외부에 노출하지 않음.
- **쿠폰 복원은 CouponFacade.restoreCoupon()**: 이미 존재하던 메서드를 활용.
- **OrderRepository는 유지**: 주문 상태 조회는 결제 도메인의 직접 관심사 (주문 → 결제 1:1 관계).

---

## 5. Self-Contained Service 관점

결제 도메인을 분석하면:

| 의존 대상 | 유형 | 판단 |
|----------|------|------|
| OrderRepository | 동기 조회 | 결제-주문 1:1이므로 허용 (같은 BC로 분류 가능) |
| ProductFacade.restoreStock() | Facade 호출 | 도메인 경계를 Facade로 격리 → 분리 시 이벤트 기반으로 전환 가능 |
| CouponFacade.restoreCoupon() | Facade 호출 | 동일 |
| PgRouter | 외부 시스템 | Circuit Breaker + Retry로 보호 완료 |

Self-Contained Service의 핵심은 "동기 의존을 최소화"하는 것이지 "의존을 제거"하는 것이 아니다. 현재 모놀리스 구조에서는 Facade 위임이 적절하며, 마이크로서비스 분리 시 이벤트 기반(Saga)으로 전환하면 된다.

---

## 6. 라이팅 포인트

1. **Decomposition 패턴은 마이크로서비스 전용이 아니다** — 모놀리스에서도 도메인 경계를 점검하는 체크리스트로 활용 가능.
2. **"추상화해야 하는가?"의 기준은 변경 가능성** — Outbox → Kafka 전환 시 Facade 코드를 건드려야 한다면, 지금 추상화할 근거가 있다.
3. **cross-domain 접근은 "동작하는가?"가 아니라 "분리 가능한가?"로 판단** — PaymentRecoveryService가 Product 도메인의 Redis 이중 쓰기를 알고 있으면, 재고 전략 변경 시 결제 코드도 수정해야 한다.
