PG 호출을 트랜잭션 밖으로 빼면 생기는 다섯 개의 빈틈


> **TL;DR**: PG 호출을 트랜잭션 밖으로 빼면 DB 커넥션 점유가 4,510ms에서 30ms로 줄어든다. 대신 빈틈이 다섯 개 생긴다. 각 빈틈마다 안전장치를 놓았다.

---

## 트랜잭션 안에서 PG를 호출하면

결제 흐름을 가장 단순하게 구현하면 이렇다.

```java
@Transactional
public void pay(Long orderId, PaymentRequest request) {
    Payment payment = Payment.create(orderId);
    paymentRepository.save(payment);

    PgResponse response = pgClient.request(request);

    payment.updateStatus(response.status());
}
```

하나의 트랜잭션 안에서 DB 저장, PG 호출, 상태 업데이트를 전부 처리한다. PG가 실패하면 롤백되니까 원자성이 보장된다.

문제는 PG 호출이 느리다는 것이다.

```
DB INSERT:    ~5ms
PG 호출:      100ms ~ 4,500ms (타임아웃 1초 × Retry 3회)
DB UPDATE:    ~5ms

트랜잭션 동안 DB 커넥션 점유: 최대 4,510ms
```

초당 100건이면 `100 × 4.5초 = 450 커넥션·초`. HikariCP가 10개면 1초 만에 고갈된다. 결제뿐 아니라 상품 조회, 주문 목록까지 전부 멈춘다.

---

## 트랜잭션을 분리했다

PG 호출을 트랜잭션 밖으로 뺐다.

```
TX-0: 쿠폰 선차감                          → ~5ms
Redis: 가주문 생성 + 재고 예약              → ~10ms
TX-1: Payment(REQUESTED) + Outbox INSERT   → ~10ms
[PG 호출]                                  → 100ms ~ 4,500ms (트랜잭션 없음)
TX-2: Payment 상태 UPDATE                  → ~5ms
```

DB 커넥션 점유가 `~30ms`로 줄었다. `100 × 0.03초 = 3 커넥션·초` — 30% 사용률이다.

대신 빈틈이 생겼다.

---

## 다섯 개의 빈틈

```
TX-1 commit ──①── PG 호출 ──②── PG 응답 → DB 저장
                                    │
                               PG 성공
                                    │
                        ┌─────③─────┼─────⑤─────┐
                        ▼           ▼           ▼
                    콜백 수신    DB 저장     Redis 조회
                        │                       │
                        ④                       │
                        ▼                       ▼
                    콜백 처리              가주문 → 진주문
```

| # | 빈틈 | 상황 | 안전장치 |
|---|------|------|---------|
| ① | TX-1 커밋 → PG 호출 사이 | 서버 크래시, PG 호출 누락 | **Transactional Outbox** |
| ② | PG 성공 → DB 저장 사이 | DB 장애, 서버 OOM | **Local WAL** |
| ③④ | PG 성공 → 콜백 수신/처리 사이 | 콜백 유실, 처리 중 예외 | **Polling Hybrid + Callback DLQ** |
| ⑤ | PG 성공 → Redis 조회 사이 | Redis 장애로 가주문 유실 | **TX-1 Payment 레코드** |

하나의 트랜잭션이었을 때는 이 빈틈이 전부 롤백으로 커버됐다. 분리한 순간 각 빈틈을 개별로 메워야 한다.

---

## TX-1이 닻이다

다섯 개의 안전장치는 서로 독립적이지만, 공통된 기준점이 있다. **TX-1 커밋** 시점에 DB에 저장되는 두 레코드다.

```
TX-1 커밋 시점에 DB에 저장되는 것:
  - Payment (orderId, amount, status=REQUESTED)
  - PaymentOutbox (paymentId, status=PENDING)
```

TX-1 이전에 장애가 나면? 아직 돈이 안 빠져나갔다. 쿠폰과 재고를 복원하면 된다.

TX-1 이후에 장애가 나면? Payment 레코드가 DB에 있다. 다섯 개의 안전장치 중 하나가 복구한다.

| 빈틈 | 복구 시 TX-1이 제공하는 것 |
|------|--------------------------|
| ① PG 미호출 | Outbox 레코드 (결제 의도) |
| ② DB 저장 실패 | orderId, transactionKey (WAL 매핑) |
| ③ 콜백 유실 | transactionKey (Polling 조회 키) |
| ④ 콜백 처리 실패 | Payment 레코드 (재처리 대상) |
| ⑤ Redis 장애 | orderId, amount (진주문 생성 정보) |

그리고 다섯 개 전부가 실패하는 최악의 경우? 배치 복구(1분)와 대사 배치(1시간)가 마지막 그물이다.

---

## 시리즈

각 빈틈의 안전장치를 깊이 다룬 글이다.

| # | 제목 | 빈틈 |
|---|------|------|
| 1 | 서킷브레이커 적용 기준: 결제 복구 경로는 왜 차단하면 안 되는가 | 복구 경로 보호 |
| 2 | TX 커밋 후 누락된 PG 호출을 복구하는 법 — Transactional Outbox | ① |
| 3 | PG 성공 후 DB 저장 실패를 복구하는 법 — Local WAL | ② |
| 4 | 콜백 유실과 처리 실패에 대비한 결제 복구 설계 — Polling Hybrid와 Callback DLQ | ③④ |
| 5 | Redis 장애에도 진주문 생성을 보장하는 결제 설계 | ⑤ |

---

## 돌아보며

빈틈이 없는 설계는 없다. 트랜잭션 하나로 감싸면 빈틈은 없지만 병목이 생기고, 분리하면 병목은 없지만 빈틈이 생긴다. 빈틈을 없애는 것이 아니라, 빈틈마다 그물을 놓는 것. 이번 설계에서 반복적으로 내린 판단이다.