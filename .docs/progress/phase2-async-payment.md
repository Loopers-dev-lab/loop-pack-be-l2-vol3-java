# Phase 2: 비동기 결제 흐름 + 트랜잭션 경계

## 학습 목표
- 요청-응답 분리 구조(비동기 결제)의 흐름을 정확히 이해한다
- 트랜잭션 경계를 올바르게 분리하여 외부 호출 중 DB 커넥션 점유를 방지한다
- 콜백 유실 시 폴링/수동 조회로 상태를 복구하는 메커니즘을 구현한다

## 비동기 결제 흐름

```
Client → POST /api/v1/payments
  │
  ├─ TX1: Payment(PENDING) + Order(PAYMENT_PENDING) 저장 → 커밋
  │
  ├─ [트랜잭션 밖] PG 호출 → transactionKey 수신 (PENDING)
  │
  ├─ TX2: Payment(IN_PROGRESS) 업데이트 → 커밋
  │
  └─ 응답: Payment(IN_PROGRESS, transactionKey)

PG 내부 처리 (1~5초 후)
  │
  └─ POST /api/v1/payments/callback
       │
       └─ TX3: Payment(PAID/FAILED) + Order(PAID/PAYMENT_FAILED) 업데이트
```

## 트랜잭션 경계 분리

### 왜 외부 호출을 @Transactional 밖에서?

```
[잘못된 방식]
@Transactional
public Payment requestPayment(...) {
    Payment payment = save(PENDING);      // DB 커넥션 점유 시작
    pgClient.requestPayment(...);         // 100ms~500ms 대기 (커넥션 계속 점유!)
    payment.markInProgress(transactionKey);
    return payment;
}                                         // 여기서야 커넥션 해방

문제: PG 호출 동안 DB 커넥션이 점유됨
→ HikariCP 커넥션 풀(10개) × PG 응답 500ms = 5초 내 커넥션 고갈 가능
→ PG와 무관한 상품 조회까지 영향
```

```
[올바른 방식]
public Payment requestPayment(...) {
    Payment payment = txHelper.initializePayment(...);  // TX1: 커넥션 즉시 반환
    String key = pgClient.requestPayment(...);          // 외부 호출 (커넥션 미점유)
    return txHelper.markInProgress(key);                // TX2: 커넥션 즉시 반환
}
```

### self-invocation 문제 해결

Spring `@Transactional`은 프록시 기반 → 같은 클래스 내 self-invocation은 프록시를 거치지 않음.

**해결**: `PaymentTransactionHelper`라는 별도 `@Component`에 `@Transactional` 메서드를 분리.

```
PaymentApplicationService (트랜잭션 없음, 조율자)
  ├── PaymentTransactionHelper (@Transactional 메서드 보유)
  ├── PaymentGateway (외부 호출, 트랜잭션 밖)
  └── PaymentDomainService (도메인 로직)
```

## 세 가지 실패 시나리오 분석

| 시나리오 | 내부 상태 | PG 상태 | 복구 방법 |
|---|---|---|---|
| TX1 성공 → PG 실패 | Payment: PENDING, Order: PAYMENT_PENDING | 미접수 | 재요청 또는 수동 sync |
| TX1 성공 → PG 성공 → TX2 실패 | Payment: PENDING, Order: PAYMENT_PENDING | PENDING(접수됨) | sync로 PG 조회 → IN_PROGRESS/PAID로 복구 |
| PG 성공 → 콜백 미수신 | Payment: IN_PROGRESS | SUCCESS/FAILED | sync로 PG 조회 → PAID/FAILED로 복구 |

**핵심**: 어떤 시점에 실패하든 PENDING 상태의 결제건을 나중에 복구 가능.

## (v2 보강) 커넥션 풀 포화 상세 시나리오

```
조건:
- HikariCP maximumPoolSize: 10
- DB 쿼리 자체 소요: 0.1초
- PG 외부 호출 지연: 4.8초 (장애 상황)
- 초당 요청: 2건

[잘못된 방식: 외부 호출이 @Transactional 안에 있을 때]

1건당 DB 커넥션 점유 시간 = DB 쿼리(0.1초) + PG 호출(4.8초) + 후처리(0.1초) = 5초
초당 2건 × 5초 점유 = 동시 10개 커넥션 점유
→ 5초 만에 커넥션 풀(10개) 포화!
→ 11번째 요청부터 SQLTransientConnectionException (커넥션 대기 타임아웃)
→ 결제뿐 아니라 상품 조회, 주문 조회 등 모든 DB 접근 불가

[올바른 방식: 외부 호출이 트랜잭션 밖]

TX1: DB 커넥션 점유 0.1초 → 즉시 반환
PG 호출: DB 커넥션 미점유 (4.8초)
TX2: DB 커넥션 점유 0.1초 → 즉시 반환

1건당 DB 커넥션 점유 시간 = 0.2초 (TX1 + TX2)
초당 2건 × 0.2초 = 동시 0.4개 커넥션 점유
→ 커넥션 풀 여유 충분!
```

## (v2 보강) 읽기 타임아웃 시 후처리

읽기 타임아웃(SocketTimeoutException)이 발생해도 **PG에서는 결제가 성공적으로 처리되었을 가능성**이 높다.

따라서 단순히 "실패"로 처리하면 안 되고, 반드시 후속 조치가 필요하다:

| 방법 | 구현 상태 | 설명 |
|---|---|---|
| **수동 상태 동기화 (sync API)** | ✅ 구현됨 | `POST /payments/{transactionKey}/sync`로 PG 조회 후 상태 복구 |
| **주기적 상태 대조 (스케줄러)** | 미구현 (향후) | PENDING/IN_PROGRESS 상태가 N분 이상 지속된 건을 자동으로 PG에 조회 |
| **성공 확인 API / 취소 API** | PG 조회 API로 대체 | PG 시뮬레이터의 `GET /payments/{transactionKey}`로 상태 확인 |

현재는 sync API로 수동 복구가 가능하며, 주기적 자동 대조는 향후 스케줄러(Phase 5+ 또는 별도 배치)로 구현할 수 있다.

## PG 시뮬레이터 실측 결과

### 결제 요청 (POST /api/v1/payments) - 20회 측정

| 항목 | 값 |
|---|---|
| 성공률 | 65% (13/20) - 설계상 60% |
| 응답 시간 범위 | 125ms ~ 479ms |
| 전부 Read Timeout(2초) 이내 | ✅ |
| HTTP 500 에러 | 35% (7/20) - 설계상 40% |

### 비동기 처리 확인

```
1. POST /payments → 200 OK, status: PENDING, transactionKey: 20260317:TR:eec4c1
2. (6초 대기)
3. GET /payments/20260317:TR:eec4c1 → status: SUCCESS, reason: "정상 승인되었습니다."
```
- 비동기 처리 지연: 1~5초 (설계대로)
- 콜백은 commerce-api가 안 떠있으므로 실패하지만, PG 조회 API로 상태 확인 가능

## 콜백 미수신 대응: sync API

콜백이 오지 않는 경우:
1. 네트워크 문제
2. 콜백 서버(commerce-api) 다운
3. PG 버그

→ `POST /api/v1/payments/{transactionKey}/sync` API로 PG에 직접 조회하여 상태 복구.

### 멱등성 보장
- 이미 PAID/FAILED인 결제에 대해 sync를 호출하면 PG 조회 없이 현재 상태 반환
- 콜백이 중복으로 들어와도 `applyPaymentResult`에서 멱등하게 처리
