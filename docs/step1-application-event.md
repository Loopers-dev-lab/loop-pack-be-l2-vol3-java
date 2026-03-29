# Step 1 — ApplicationEvent로 경계 나누기

## 핵심 질문: 이걸 이벤트로 분리해야 하는가?

이벤트 분리는 무조건 좋은 게 아니다. 판단 기준이 먼저다.

### 이벤트로 분리해도 되는 로직

| 기준 | 설명 |
|---|---|
| 실패해도 핵심 비즈니스에 영향 없어야 한다 | 알림 전송 실패 → 결제 취소되면 안 됨 |
| eventual consistency 허용 | 즉시 반영 안 돼도 됨 |
| 다른 bounded context의 관심사 | 로깅, 알림, 집계 등 |

### 이벤트로 분리하면 안 되는 로직

| 기준 | 설명 |
|---|---|
| 실패 시 메인 트랜잭션도 롤백돼야 한다 | 핵심 비즈니스 로직 |
| 강한 일관성 필요 | 즉시 반영이 보장돼야 함 |
| 복구 수단이 없다 | ApplicationEvent는 DLQ 없음 |

---

## ApplicationEvent vs Kafka

```
ApplicationEventPublisher       Kafka
──────────────────────────────────────────────
JVM 메모리에만 존재            디스크에 저장
서버 재시작 → 유실             서버 재시작 → 남아있음
실패 → 그냥 소멸               실패 → DLQ로 이동
재시도 없음                    재시도 설정 가능
같은 서버 안에서만 동작         다른 서버(마이크로서비스)도 가능
```

**결론**: ApplicationEvent는 "실패해도 괜찮은 부가 로직"에만 써야 한다.
핵심 로직은 직접 호출이나 Kafka(재시도/DLQ 보장)를 써야 한다.

---

## 주문-결제 플로우 분석

### 같은 스레드, 독립된 트랜잭션

```
스레드 A ────────────────────────────────────────────────────▶

  handleCallback() 호출 (@Transactional 없음)
  │
  ├─ paymentService.handleCallback()
  │    ┌─────────────────────────────┐
  │    │  TX1 시작                    │
  │    │  payment.complete()          │
  │    │  이벤트 발행                  │
  │    │  TX1 커밋 ✅                  │
  │    └─────────────────────────────┘
  │
  ├─ orderService.confirmOrder()
  │    ┌─────────────────────────────┐
  │    │  TX2 시작                    │
  │    │  order.confirm()             │
  │    │  TX2 커밋 ✅                  │
  │    └─────────────────────────────┘
  │
  └─ 끝
```

TX1이 끝나고 TX2가 시작된다. 같은 스레드지만 트랜잭션은 완전히 별개다.

### 왜 confirmOrder를 이벤트로 분리하면 안 되는가

- `ApplicationEventPublisher`는 인메모리 → DLQ 없음
- 리스너 실패 시 이벤트 소멸
- 결제는 `COMPLETED`인데 주문은 영원히 `CREATED` 상태로 남음
- **source of truth는 결제** → 결제 완료면 주문 확정은 반드시 일어나야 함

### 결론

| 로직 | 처리 방식 | 이유 |
|---|---|---|
| 주문 확정 (`confirmOrder`) | 직접 호출 | 결제와 반드시 함께, 실패 시 복구 불가 |
| 알림 전송 | ApplicationEvent | 실패해도 결제에 영향 없어야 함 |
| 유저 행동 로그 | ApplicationEvent | 실패해도 결제에 영향 없어야 함 |

---

## 이벤트 리스너 어노테이션 3개 조합

```java
@Async                                            // 1. 별도 스레드
@Transactional(propagation = REQUIRES_NEW)        // 2. 새 트랜잭션
@TransactionalEventListener(phase = AFTER_COMMIT) // 3. 커밋 후 실행
public void handlePaymentCompleted(PaymentCompletedEvent event) { ... }
```

### 각각의 역할

**`@Async` — 왜 비동기인가?**
- 알림/로그가 느려도 결제 응답 속도에 영향을 주면 안 된다
- 별도 스레드로 넘겨서 결제 응답을 먼저 반환한다

**`REQUIRES_NEW` — 왜 새 트랜잭션인가?**
- Spring 트랜잭션은 ThreadLocal에 묶여 있다
- `@Async`로 스레드가 바뀌면 원래 트랜잭션 컨텍스트가 없다
- `REQUIRES_NEW`로 새 스레드에서 독립적인 트랜잭션을 만든다

```
결제 TX (스레드 A)
  └─ payment.complete() → 커밋
  └─ AFTER_COMMIT 발동
       └─ @Async → 스레드 B로 넘김
            └─ REQUIRES_NEW → 스레드 B에서 새 TX 시작
                 └─ 로그/알림/DB 작업
```

**`AFTER_COMMIT` — 왜 커밋 후인가?**
- 트랜잭션이 롤백됐는데 알림이 나가면 안 된다
- 결제가 실제로 완료(커밋)된 경우에만 알림을 보낸다

---

## 구현 결과

### 발행: PaymentService.handleCallback()

```java
@Transactional
public void handleCallback(Long orderId, String transactionId, boolean success) {
    Payment payment = paymentRepository.findByOrderId(orderId)...;
    if (success) {
        payment.complete(transactionId);
        eventPublisher.publishEvent(new PaymentCompletedEvent(orderId, payment.getMemberId(), payment.getAmount()));
    } else {
        payment.fail();
    }
}
```

트랜잭션 안에서 발행해야 `AFTER_COMMIT`이 동작한다.
Facade처럼 `@Transactional`이 없는 곳에서 발행하면 리스너가 실행되지 않는다.

### 리스너: PaymentEventListener

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
public void handlePaymentCompleted(PaymentCompletedEvent event) {
    log.info("[알림] 결제가 완료되었습니다. orderId={}, memberId={}, amount={}",
        event.orderId(), event.memberId(), event.amount());
}
```

### 테스트: @RecordApplicationEvents

```java
@RecordApplicationEvents
@SpringBootTest
class PaymentEventListenerTest {

    @Autowired
    ApplicationEvents applicationEvents;

    @Test
    void publishesPaymentCompletedEvent_whenCallbackSucceeds() {
        // arrange
        Payment payment = savedPendingPayment();

        // act
        paymentService.handleCallback(payment.getOrderId(), "tx-001", true);

        // assert
        assertThat(applicationEvents.stream(PaymentCompletedEvent.class))
            .hasSize(1)
            .first()
            .satisfies(event -> {
                assertThat(event.orderId()).isEqualTo(payment.getOrderId());
            });
    }
}
```

`@RecordApplicationEvents`는 "이벤트가 발행됐는가"를 검증한다.
리스너 실행 자체는 `@Async`로 별도 스레드라 타이밍 보장이 없어 테스트하지 않는다.

---

## Like 집계 — Eventual Consistency

```
addLike() 호출
  └─ TX: like 저장 + LikeCreatedEvent 발행 → 커밋
       └─ AFTER_COMMIT (별도 스레드)
            └─ REQUIRES_NEW TX: product.likeCount + 1 → 커밋
```

- `like` 저장과 `likeCount` 증가는 **다른 트랜잭션**
- 짧은 순간 likeCount가 실제보다 낮을 수 있다 → **eventual consistency**
- likeCount 집계 실패해도 좋아요 자체는 성공 → 부가 로직이므로 허용

---

## 주문-결제 자동화 플로우 설계

### 요구사항
- 주문하기 버튼 → 주문 생성 + 결제 자동 실행
- 결제 실패 시 사용자가 직접 결제 재시도 가능
- 첫 결제 시 카드 정보 저장, 이후 주문 시 저장된 카드 자동 사용
- 카드는 여러 개 등록 가능, 기본 카드(is_default) 존재

### 각 로직의 실패 허용 범위

| 로직 | 실패 시 주문 영향 | 이유 |
|---|---|---|
| Order 생성 | 주문 실패 | 핵심 로직 |
| Payment 생성 | 주문 성공 유지 | Payment는 별도 관심사 |
| user_card 저장 | 주문 성공 유지 | 부가 로직, 나중에 재시도 |
| PG 결제 실행 | 주문 성공 유지 | 외부 시스템, 재시도 가능 |

→ Payment 생성, user_card 저장, PG 호출 모두 **이벤트로 분리**

### 카드 정보는 어디서?

- Order 테이블 ❌ → Payment의 관심사
- user_card ❌ → 저장 전일 수 있음 (리스너 실패 가능)
- **OrderCreatedEvent에 담아서 전달** ✅

```java
public record OrderCreatedEvent(
    Long orderId,
    Long memberId,
    long amount,
    CardType cardType,  // 카드 정보를 이벤트에 실어 보냄
    String cardNo
) {}
```

### 최종 플로우

```
POST /api/v1/orders { items, cardType, cardNo }

주문 생성 TX (Order만)
  └─ 커밋
       └─ OrderCreatedEvent 발행
            ├─ 리스너 A (AFTER_COMMIT, Async): Payment(PENDING) 생성 + PG 호출
            └─ 리스너 B (AFTER_COMMIT, Async): user_card 저장 (실패 시 알림)

결제 실패 시 재시도:
POST /api/v1/payments (기존 API 유지)
```

### 변경 사항 목록

| 항목 | 내용 |
|---|---|
| `user_card` 테이블 추가 | id, user_id, card_type, card_no, is_default |
| `OrderCreatedEvent` 추가 | orderId, memberId, amount, cardType, cardNo |
| `OrderFacade.createOrder()` 수정 | 카드 정보 받아서 이벤트 발행 |
| `OrderEventListener` 추가 | 리스너 A(결제), 리스너 B(카드 저장) |
| `POST /api/v1/payments` | 결제 실패 시 재시도용으로 유지 |

---

## 현재 코드에서 이벤트로 분리할 것들

"이벤트로 분리해야 한다"는 게 항상 옳은 게 아니다. 부가 로직이 없으면 분리할 것도 없다.

| 플로우 | 상태 | 이유 |
|---|---|---|
| 좋아요 집계 | ✅ 이미 구현 | likeCount는 부가 집계 |
| 브랜드 비활성화 → 상품 비활성화 | ✅ 이미 구현 | 상품 처리는 부가 로직 |
| 결제 완료 → 알림/로그 | ✅ 오늘 구현 | 실패해도 결제에 영향 없어야 함 |
| 주문 생성 | 부가 로직 없음 | 전부 주요 로직 (인증, 재고차감, 쿠폰) |
| 결제 요청 | 부가 로직 없음 | PG 호출은 이미 TX 밖에 설계됨 |

**억지로 이벤트를 만들 필요 없다. 부가 로직이 없으면 분리 대상도 없다.**

---

## @EventListener vs @TransactionalEventListener

이벤트 리스너 어노테이션은 발행 위치의 트랜잭션 유무에 따라 선택한다.

| 상황 | 어노테이션 | 이유 |
|---|---|---|
| 트랜잭션 없는 곳에서 발행 (Controller 등) | `@EventListener` | 트랜잭션이 없으니 커밋 대기 불필요 |
| 트랜잭션 커밋 후 실행 보장 | `@TransactionalEventListener(AFTER_COMMIT)` | 롤백 시 리스너 실행 안 됨 |

### 예시: ProductViewedEvent

```
Controller (트랜잭션 없음)
  └─ eventPublisher.publishEvent(ProductViewedEvent)
       └─ @TransactionalEventListener → 실행 안 됨 ❌ (트랜잭션 없음)
       └─ @EventListener → 즉시 실행 ✅
```

### @Cacheable 주의사항

`@Cacheable`이 붙은 메서드는 캐시 히트 시 메서드 내부가 실행되지 않는다.
이벤트를 Facade에서 발행하면 캐시 히트 시 이벤트가 발행되지 않는다.
→ **모든 요청을 기록하려면 Controller에서 발행해야 한다.**

---

## 유저 행동 로깅

### 설계 결정

| 결정 | 이유 |
|---|---|
| 파일 로그 (log.info) | 지금은 분석 시스템 없음, Kafka 단계에서 확장 |
| User-Agent 원본 저장 | 파싱 라이브러리 불필요, 분석은 외부 시스템에서 |
| 비로그인 userId = "unknown" | null보다 명시적, 로그 분석 시 필터링 용이 |
| 이벤트별 분리 | 하나 실패해도 다른 것에 영향 없음 |
| 전용 리스너 클래스 분리 | 로그 방식 변경 시 이 파일만 수정 |

### 구현

```java
// ProductViewedEvent — 트랜잭션 없는 Controller에서 발행
public record ProductViewedEvent(String userId, Long productId, String userAgent) {}

// UserActivityLogListener — 세 가지 이벤트 모두 수신
@Async @EventListener
public void handleProductViewed(ProductViewedEvent event) { ... }  // 트랜잭션 없음

@Async @TransactionalEventListener(AFTER_COMMIT)
public void handleLikeCreated(LikeCreatedEvent event) { ... }      // 트랜잭션 있음

@Async @TransactionalEventListener(AFTER_COMMIT)
public void handleOrderCreated(OrderCreatedEvent event) { ... }    // 트랜잭션 있음
```

---

## Command vs Event

| | Command | Event |
|---|---|---|
| 의미 | "해줘" (미래 지향) | "일어났다" (과거) |
| 수신자 | 특정 핸들러 (1:1) | 모든 리스너 (1:N) |
| 발행자가 수신자를 아는가 | 안다 | 모른다 |
| 예시 | `ConfirmOrderCommand` | `PaymentCompletedEvent` |
| 사용처 | Kafka 기반 마이크로서비스 연동 | ApplicationEvent, Kafka 토픽 |

Step 1은 ApplicationEvent 기반 **Event 패턴**.
Command 패턴은 Kafka 연동 시 본격 적용 (주문 생성 → Kafka → 재고 서비스 소비).