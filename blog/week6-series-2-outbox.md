TX 커밋 후 누락된 PG 호출을 복구하는 법 — Transactional Outbox


> **TL;DR**: Payment와 Outbox를 같은 트랜잭션에서 저장한다. 서버가 TX-1 커밋 직후에 죽어도, 5초 후 Outbox Poller가 빠진 PG 호출을 대신 실행한다.

---

## 빈틈이 생기는 지점

TX-1에서 Payment를 저장하고, 그다음에 PG를 호출한다. 이 사이에 서버가 죽으면 Payment는 DB에 있는데 PG 호출은 안 된 상태가 된다.

```
TX-1: Payment(REQUESTED) + Outbox(PENDING) → commit ✓
                                                     ← 서버 크래시
[PG 호출]                                            ← 실행 안 됨
```

Payment의 상태는 `REQUESTED`다. PG에 요청을 보냈다는 기록이 없다. 배치 복구(1분 주기)가 잡아내긴 하지만, 1분은 길다.

---

## Outbox 없이 배치만 쓸 때의 문제

배치 복구만으로도 이 빈틈을 메울 수 있다. 1분마다 `REQUESTED` 상태인 Payment를 찾아서 PG에 다시 요청하면 된다.

하지만 배치에는 한계가 있다.

| 관점 | 배치만 | Outbox + 배치 |
|------|--------|--------------|
| 복구 지연 | 최대 1분 | 최대 5초 |
| 복구 대상 식별 | Payment 상태 기반 (암묵적) | Outbox 상태 기반 (명시적) |
| PG 호출 의도 | 추론해야 함 | 레코드로 보존 |

Payment가 `REQUESTED` 상태라는 것만으로는 "PG 호출이 안 된 건"인지 "PG 호출은 했는데 응답 전에 죽은 건"인지 구분할 수 없다. Outbox는 "이 결제를 PG에 보내야 한다"는 의도 자체를 레코드로 남긴다.

---

## Outbox의 동작

TX-1에서 Payment와 Outbox를 같은 트랜잭션으로 저장한다.

```java
// TX-1 — Payment + Outbox 원자적 저장
Payment payment = Payment.create(orderId, REQUESTED);
paymentRepository.save(payment);

PaymentOutbox outbox = PaymentOutbox.create(payment.getId());
outboxRepository.save(outbox);
// TX-1 commit → 둘 다 저장되거나, 둘 다 안 된다
```

같은 트랜잭션이므로 Payment만 저장되고 Outbox는 안 되는 상황은 발생하지 않는다.

Outbox Poller는 5초마다 `PENDING` 상태인 Outbox를 조회한다.

```
[Outbox Poller — 5초 주기]
1. PaymentOutbox에서 status = 'PENDING' 조회
2. 각 건에 대해:
   a. Payment 현재 상태 확인
      → 이미 PAID/FAILED → Outbox PROCESSED (다른 경로에서 처리됨)
   b. PG에 orderId로 조회: "이 주문 결제 기록 있어?"
      → 있음 → transactionKey 저장 + Outbox PROCESSED
      → 없음 → PG 결제 요청 (POST) 실행
   c. retry_count 증가, 최대 3회 초과 시 Outbox FAILED
```

---

## 멱등성 확인이 먼저다

Outbox Poller가 PG 결제 요청을 보내기 전에, 먼저 PG에 "이 주문 기록 있어?"라고 물어본다.

```java
// Outbox Poller — PG 호출 전 멱등성 확인
PgPaymentStatusResponse existing = pgRouter.getPaymentByOrderId(orderId, pgProvider);

if (existing != null && !"UNKNOWN".equals(existing.status())) {
    // PG에 이미 기록이 있다 → 중복 요청 방지
    outbox.markProcessed();
    return;
}

// PG에 기록이 없다 → 안전하게 결제 요청
pgRouter.requestPayment(request);
```

이 순서가 중요하다. "서버가 PG 호출 직후, 응답을 받기 전에 죽은 경우"를 생각하면 된다. PG는 요청을 받아서 처리했는데 우리는 그 사실을 모른다. Outbox Poller가 다시 실행되면 PG에 중복 요청을 보낼 수 있다. 먼저 물어보면 이 문제가 사라진다.

---

## Outbox 테이블

```sql
CREATE TABLE payment_outbox (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id   BIGINT NOT NULL,
    order_id     VARCHAR(50) NOT NULL,
    event_type   VARCHAR(30) NOT NULL DEFAULT 'PAYMENT_REQUEST',
    payload      TEXT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME NOT NULL,
    processed_at DATETIME,
    retry_count  INT DEFAULT 0,
    INDEX idx_outbox_status (status)
);
```

`retry_count`를 두는 이유가 있다. PG가 완전히 죽어 있으면 Outbox Poller도 계속 실패한다. 무한 재시도는 의미가 없으므로 3회 초과하면 `FAILED`로 전환하고 알림을 보낸다. 이후는 배치 복구(1분)나 수동 복구 API가 담당한다.

---

## Outbox Poller와 배치 복구의 관계

둘은 경쟁이 아니라 계층이다.

```
[5초]  Outbox Poller  → PG 호출 누락 재시도 (빠른 복구)
[1분]  Batch Recovery → Outbox Poller가 놓친 건 + Outbox Poller 자체 장애 대비
```

Outbox Poller가 정상 동작하면 배치 복구가 처리할 건이 없다. 배치는 Outbox Poller의 안전망이다. Outbox Poller 프로세스 자체가 죽어 있으면 배치가 1분 후에 잡아낸다.

이 구조는 MSA로 전환할 때도 유리하다. Outbox 레코드를 DB INSERT 대신 Kafka로 발행하면, Outbox Poller가 Kafka Consumer로 바뀐다. 패턴 자체는 변하지 않는다.

---

## 돌아보며

Outbox의 핵심은 "PG를 호출하겠다는 의도"를 Payment와 같은 트랜잭션에서 기록하는 것이다. 의도가 기록되어 있으면, 실행이 빠졌을 때 누군가가 대신 실행할 수 있다. 기록이 없으면 빠졌다는 사실 자체를 알 수 없다.

Outbox 테이블 하나와 5초짜리 스케줄러 하나. 복구 지연이 1분에서 5초로 줄었다.
