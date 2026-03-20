콜백 유실과 처리 실패에 대비한 결제 복구 설계 — Polling Hybrid와 Callback DLQ


> **TL;DR**: 비동기 PG의 결제 결과는 콜백으로 온다. 콜백이 유실되면 결제 상태가 영원히 PENDING이다. 10초 후 PG에 직접 물어보는 Polling Hybrid를 추가했고, 콜백이 왔지만 처리 중 실패하는 경우를 위해 Callback Inbox(DLQ)를 두었다.

---

## 콜백에 의존하는 구조

PG 시뮬레이터는 비동기 결제다. 결제 요청을 보내면 즉시 `PENDING`을 반환하고, 1~5초 후에 콜백으로 최종 결과를 보낸다.

```
Client → POST /payments → PG: "접수, PENDING" → Client: "결제 처리 중"
                                                  ... 1~5초 후 ...
PG → POST /payments/callback → 우리: "결제 성공"
```

문제는 PG 시뮬레이터의 콜백이 재시도하지 않는다는 것이다. 전송 실패 시 로그만 남긴다. 콜백이 유실되면 우리 쪽 Payment 상태는 `PENDING`인 채로 남는다.

---

## 빈틈 ③ — 콜백이 안 온다

콜백 유실은 여러 원인으로 발생한다.

```
PG: "콜백 보낼게" → 네트워크 장애 → 우리 서버에 도달 못 함
PG: "콜백 보낼게" → 우리 서버 재시작 중 → 수신 실패
PG: "콜백 보낼게" → 로드밸런서가 다른 인스턴스로 보냄 → 유실
```

배치 복구(1분)가 잡아내긴 한다. 하지만 결제를 했는데 1분간 결과를 모르면 사용자는 불안해서 다시 결제 버튼을 누른다.

---

## Polling Hybrid — 10초 후 직접 물어본다

콜백을 기다리기만 하지 않는다. PG 응답이 `PENDING`이면 10초짜리 Delayed Task를 등록한다.

```
PG 응답 PENDING 수신
  → 정상 경로: 콜백 대기
  → 보험: Delayed Task 등록 (T+10초)

10초 내 콜백 수신 → Task 취소 (정상 경로)
10초 후 콜백 미수신 → Task 실행:
  → GET /payments/{transactionKey}  (PG에 직접 조회)
  → SUCCESS → 조건부 UPDATE → PAID
  → FAILED → 조건부 UPDATE → FAILED
  → PENDING → 아직 처리 중 → 다음 주기에 재확인
```

```java
taskScheduler.schedule(
    () -> paymentRecoveryService.checkAndRecover(paymentId),
    Instant.now().plusSeconds(10)
);
```

10초의 근거: PG 비동기 처리 최대 5초 + 콜백 전송 시간을 고려하면, 10초 후에도 콜백이 안 왔다면 유실 가능성이 높다.

---

## 콜백과 Polling이 동시에 실행되면

콜백이 9초에 도착하고, Polling이 10초에 실행되면 둘 다 같은 Payment를 PAID로 바꾸려 한다.

조건부 UPDATE가 이 문제를 해결한다.

```sql
UPDATE payment
SET status = 'PAID'
WHERE id = ? AND status IN ('PENDING', 'UNKNOWN')
```

먼저 실행된 쪽이 `affected rows = 1`을 얻고, 늦게 실행된 쪽은 `affected rows = 0`을 얻는다. 0이면 이미 처리된 건으로 판단하고 넘어간다. 락이 필요 없다.

```java
int affected = paymentRepository.updateStatusConditional(
    paymentId, PaymentStatus.PAID,
    List.of(PaymentStatus.PENDING, PaymentStatus.UNKNOWN));

if (affected == 0) {
    // 이미 다른 경로에서 처리됨 → 무시
    return;
}
// 진주문 전환 진행
```

---

## 빈틈 ④ — 콜백은 왔는데 처리가 실패한다

콜백이 도착했지만, 상태 전이 중에 예외가 터질 수 있다.

```
PG 콜백 수신 → 200 OK 반환 → 조건부 UPDATE 시도 → DB 예외
→ 콜백은 수신했는데 처리가 안 됨
→ PG는 200 받았으니 재전송 안 함
→ 결제 상태 PENDING 유지
```

PG에게 200을 반환한 이상 PG는 콜백을 다시 보내지 않는다. 그런데 처리가 안 됐다. 콜백 데이터가 메모리에서 사라지면 복구할 수 없다.

---

## Callback Inbox — 원본을 먼저 저장한다

콜백을 받는 즉시 원본을 DB에 저장한다. 처리는 그다음이다.

```java
// 1단계: 원본 보존
CallbackInbox inbox = CallbackInbox.create(
    transactionKey, orderId, pgStatus, payload);
callbackInboxRepository.save(inbox);   // status = RECEIVED

// PG에게 즉시 200 OK 반환

// 2단계: 비즈니스 처리
try {
    processCallback(transactionKey, pgStatus);
    inbox.markProcessed();
} catch (Exception e) {
    inbox.recordError(e.getMessage());
    // RECEIVED 상태 유지 → DLQ 스케줄러가 재처리
}
```

처리가 실패해도 `callback_inbox` 테이블에 원본이 남아 있다.

```sql
CREATE TABLE callback_inbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    transaction_key VARCHAR(50) NOT NULL,
    order_id        VARCHAR(50) NOT NULL,
    pg_status       VARCHAR(20) NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    received_at     DATETIME NOT NULL,
    processed_at    DATETIME,
    retry_count     INT DEFAULT 0,
    error_message   VARCHAR(500),
    INDEX idx_callback_status (status)
);
```

DLQ 스케줄러가 30초마다 `RECEIVED` 상태(미처리)인 콜백을 재시도한다.

```
[Callback DLQ — 30초 주기]
1. callback_inbox에서 status = 'RECEIVED' 조회
2. 각 건에 대해 processCallback() 재실행
3. 성공 → PROCESSED / 실패 → retry_count 증가
4. retry_count 초과 → FAILED + 알림
```

---

## PG에게 200을 먼저 반환하는 이유

PG 입장에서 콜백 전송의 성공/실패는 HTTP 응답 코드로 판단한다. 우리가 500을 반환하면 PG가 재전송할 수도 있고, 타임아웃으로 판단할 수도 있다. PG의 동작은 우리가 통제할 수 없다.

그래서 콜백 "수신"과 "처리"를 분리한다. 수신은 200으로 확인해주고, 처리는 우리 내부 문제로 가져온다. 원본을 보존했으니 몇 번이든 재처리할 수 있다.

```
콜백 수신 (외부 경계) → 200 OK + 원본 DB 저장
콜백 처리 (내부 경계) → 실패해도 재시도 가능
```

---

## 복구 계층 정리

콜백 관련 빈틈에는 세 겹의 그물이 있다.

| 계층 | 동작 | 복구 시점 |
|------|------|----------|
| Callback DLQ | 콜백 수신했지만 처리 실패 → 30초마다 재시도 | 30초 |
| Polling Hybrid | 콜백 자체가 안 옴 → 10초 후 PG에 직접 조회 | 10초 |
| Batch Recovery | 위 둘 다 실패 → 1분 주기 최종 안전망 | 1분 |

각 계층은 독립적이다. Polling이 성공하면 DLQ가 처리할 게 없고, DLQ가 성공하면 배치가 처리할 게 없다. 하나가 빠져도 다른 계층이 잡아낸다.

---

## 돌아보며

콜백 기반 비동기 시스템에서 "콜백이 반드시 온다"고 가정하면 안 된다. 오지 않을 수 있고, 와도 처리가 실패할 수 있다.

Polling Hybrid는 "오지 않는" 경우를, Callback Inbox는 "왔지만 처리 실패"하는 경우를 담당한다. 두 문제의 성격이 다르니 해법도 다르다. 하나로 묶으면 깔끔해 보이지만, 분리하는 편이 각각의 실패 원인을 명확하게 추적할 수 있었다.
