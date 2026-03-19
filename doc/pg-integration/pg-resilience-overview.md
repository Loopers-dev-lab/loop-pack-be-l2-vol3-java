# PG 연동 & Resilience 설계

> **Round 6** - 외부 PG 시스템 연동, 서킷브레이커, Fallback, 쿠폰 복원 전략

---

## 1. 전체 결제 아키텍처

> **핵심 원칙**: 외부 PG 호출은 DB 트랜잭션 밖에서 실행

```
/* 핵심 원칙: 외부 PG 호출은 DB 트랜잭션 밖에서 실행 */

[ Client ]
     |
     |  POST /api/v1/payments
     v
[ PaymentV1Controller ]
     |
     v
[ PaymentFacade ] ─── 결제 흐름 조정 (트랜잭션 분리)
     |
     |── [TX1] ── PaymentTransactionService.preparePayment()
     |         ├─ Order 상태: CREATED → PAYMENT_PENDING
     |         ├─ Payment 생성: REQUESTED → PENDING
     |         └─ OrderHistory 기록
     |
     |── [No TX] ── PgPaymentGateway.requestPayment()  ← 외부 PG 호출
     |            ├─ @Retry(3회, 1초 간격)
     |            ├─ @CircuitBreaker(실패율 50% → OPEN)
     |            └─ fallbackMethod → PENDING 응답 반환 (예외 아님!)
     |
     |── [TX2 - PG 즉시 성공] ── PaymentTransactionService.completePayment()
     |         ├─ Payment: SUCCESS
     |         └─ Order: PAID
     |
     └── [PENDING 유지 - PG 장애/타임아웃]
               ├─ fallback이 PENDING 응답 반환 → 사용자에게 200 OK
               ├─ Payment: PENDING 상태 유지
               └─ 스케줄러가 PG 조회 후 최종 상태 확정
```

---

## 2. Resilience4j 설계

> **실행 순서**: `@Retry (바깥)` → `@CircuitBreaker (안쪽)` → `실제 PG 호출`

| 항목 | 설정값 | 설명 |
|------|--------|------|
| **Retry** | maxAttempts: **3**, waitDuration: **1s** | FeignException, IOException 대상 |
| **CircuitBreaker** | failureRate: **50%** → OPEN | slidingWindow: 10, HALF_OPEN: 3건 허용 |
| **Feign Timeout** | connect: **3s**, read: **5s** | PG 응답 대기 시간 |
| **Fallback** | 결제: **PENDING 반환** / 조회: **CoreException** | 메서드별 다른 전략 |

### retry-exceptions 설정이 중요한 이유

```
retry-exceptions:
  - feign.FeignException        ← PG 500 에러 → Retry ✅
  - java.io.IOException          ← 네트워크 에러 → Retry ✅
  - CallNotPermittedException    ← 목록에 없음 → Retry 안 함 ❌
```

### Case별 동작

**Case 1: PG 정상 (서킷 CLOSED)**
```
요청 → Retry → CB(CLOSED) → PG 호출 → 200 OK → 성공 반환
```

**Case 2: PG 500 에러 (서킷 CLOSED, Retry 소진)**
```
요청 → Retry(1/3) → CB(CLOSED) → PG → 500 FeignException → retry-exceptions에 있음 → 재시도
     → Retry(2/3) → CB(CLOSED) → PG → 500 FeignException → 재시도
     → Retry(3/3) → CB(CLOSED) → PG → 500 FeignException → Retry 소진
     → CB 실패 기록 → fallbackMethod 실행
```

**Case 3: 서킷 OPEN (실패율 50% 초과)**
```
요청 → Retry(1/3) → CB(OPEN) → CallNotPermittedException
     → retry-exceptions에 없음 → Retry 재시도 안 함
     → fallbackMethod 즉시 실행 → PENDING 응답 반환
     → PG 호출 없이 즉시 PENDING 응답 (fast-fail) ~20ms
```

> **왜 이 순서가 중요한가?**
>
> `retry-exceptions` 제한이 없다면:
> 서킷 OPEN → CallNotPermittedException → Retry 재시도(무의미) → 또 → 또 → **3번이나 헛수고**
>
> `retry-exceptions`로 제한했기 때문에:
> 서킷 OPEN → CallNotPermittedException → **Retry 스킵 → 즉시 fallback (1회만)**

---

## 3. Fallback 메서드 구현

### Before (fallback 없음)

```java
@CircuitBreaker(name = "pgPayment")
public PaymentGatewayResponse requestPayment(
    String userId, PaymentGatewayRequest request) {
    // PG 호출
}

// CallNotPermittedException이 그대로 전파
// → Throwable 핸들러 → "일시적인 오류"
// → 서킷 차단인지 PG 에러인지 구분 불가
```

### After (PENDING 유지 방식)

```java
@CircuitBreaker(name = "pgPayment",
    fallbackMethod = "requestPaymentFallback")
public PaymentGatewayResponse requestPayment(
    String userId, PaymentGatewayRequest request) {
    // PG 호출
}

private PaymentGatewayResponse requestPaymentFallback(
    String userId, PaymentGatewayRequest request,
    Exception e) {
    log.warn("PG fallback: orderId={}, error={}",
        request.orderId(), e.getMessage());
    return new PaymentGatewayResponse(
        null, "PENDING",
        "PG 서비스 불안정으로 결제 처리 대기 중");
}

// PaymentFacade에서:
if (pgResponse.transactionKey() != null) {
    completePayment(payment, txnKey, "결제 완료");
} else {
    log.info("결제 처리 대기: orderId={}",
        orderId);  // PENDING 유지 → 스케줄러 위임
}
```

> **핵심 원칙 — "모르면 실패 처리하지 않는다"**
>
> PG 타임아웃 ≠ 결제 실패. PG가 실제로 처리했을 수 있으므로, PENDING 상태를 유지하고
> 스케줄러가 PG 조회 후 최종 상태를 확정합니다. 사용자에게는 200 OK + "처리 대기 중" 응답을 반환합니다.

---

## 4. 쿠폰 복원 전략

### 즉시 복원 vs 확정 후 복원

| | 즉시 복원 (위험) | 확정 후 복원 (안전) |
|---|---|---|
| **흐름** | PG 타임아웃 → 쿠폰 즉시 복원 → 사용자가 다른 주문에 사용 → 복구 스케줄러: "PG에서는 성공이었네..." | PG 타임아웃 → 쿠폰 복원 안 함 → PG 콜백/스케줄러로 확정 후 복원 |
| **결과** | 쿠폰 1장으로 2건 할인 = **이중 사용** | PG가 확정적으로 실패라고 알려준 경우만 복원 |

### 쿠폰 복원 시점 정리

```
시점 1: PG 장애/타임아웃 (Fallback → PENDING 유지)
  Payment: PENDING 상태 유지, Order: PAYMENT_PENDING
  쿠폰 복원: ❌ 안 함  ← PG에서 실제로 처리됐을 수 있음 (타임아웃 등)

시점 1-1: TTL 3분 초과 (스케줄러 handleNoTransactionFound)
  PG에 거래 내역 없음 + 3분 경과 → Payment: FAILED, Order: PAYMENT_FAILED
  쿠폰 복원: ✅ 복원  ← 충분한 대기 후 미처리 확정

시점 2: PG 콜백 FAILED 수신 (handleCallback)
  Payment → FAILED, Order → PAYMENT_FAILED
  쿠폰 복원: ✅ 복원  ← PG가 확정적으로 실패라고 알려줌

시점 3: 복구 스케줄러 FAILED 확정 (recoverPayment)
  PG 조회 → 거래 상태 FAILED 확인
  Payment → FAILED, Order → PAYMENT_FAILED
  쿠폰 복원: ✅ 복원  ← PG 조회로 실패 확정

─────────────────────────────────────────────
복원 흐름:
  Order.getUserCouponId() → null이면 skip
                          → 존재하면 CouponService.restoreUserCoupon()
                                      → UserCoupon.restore() (USED → AVAILABLE)
```

---

## 5. 상태 전이 다이어그램

### Order 상태

```
CREATED ──(결제요청)──→ PAYMENT_PENDING ──(PG 성공/콜백 SUCCESS)──→ PAID
                                │
                                └──(PG 실패/콜백 FAILED)──→ PAYMENT_FAILED
```

### Payment 상태

```
REQUESTED ──(preparePayment)──→ PENDING ──(PG 성공)──→ SUCCESS
                                    │
                                    └──(PG 실패)──→ FAILED
```

### UserCoupon 상태

```
AVAILABLE ──(주문생성 시 use())──→ USED ──(결제 확정 실패 시 restore())──→ AVAILABLE
```

---

## 6. k6 부하 테스트 결과

> 서킷브레이커 + Fallback 동작 검증

### 요청 결과

| 항목 | 건수 | 비율 | 설명 |
|------|------|------|------|
| 총 요청 | **300건** | 100% | 5 req/s × 60초 |
| PG 즉시 성공 | **1건** | 0.3% | PG 정상 응답 → 즉시 SUCCESS |
| PENDING (Fallback) | **204건** | 68% | PG 장애 → Fallback → PENDING 유지, 사용자 200 OK |
| 비즈니스 에러 | **95건** | 31.6% | 주문 상태 PAYMENT_PENDING (중복 요청) |

### 응답 시간

| 항목 | 값 | 설명 |
|------|-----|------|
| 평균 | **20ms** | Fallback: PG 호출 없이 즉시 PENDING 응답 |
| p95 | **24ms** | 대부분 fast-fail로 빠른 응답 |
| 최대 | **470ms** | 첫 요청 시 PG 호출 시도 포함 |

### 응답 시간 비교

| | PENDING Fallback (서킷 OPEN) | PG 실제 호출 (HALF_OPEN 성공) |
|---|---|---|
| **응답 시간** | 12~23ms | 12~470ms |
| **동작** | PG 호출 없이 즉시 PENDING 반환 | PG 정상 시 즉시 SUCCESS |
| **사용자 응답** | 200 OK + "처리 대기 중" | 200 OK + SUCCESS |

### k6 실시간 로그

```
/* 서킷브레이커 OPEN 상태 (이전 PG 장애로 서킷 열림) */

22:40:43  [216ms] ⏳ PENDING (fallback) - orderId: 1116  ← fallback → PENDING 응답 (200 OK)
22:40:43  [23ms]  ⏳ PENDING (fallback) - orderId: 1046
22:40:44  [23ms]  ⏳ PENDING (fallback) - orderId: 1066
22:40:44  [15ms]  ⏳ PENDING (fallback) - orderId: 1076  ← 15~23ms: PG 호출 없이 즉시 응답
22:40:44  [18ms]  ⏳ PENDING (fallback) - orderId: 1036

/* 계속 PENDING fallback 응답 — 사용자에게는 200 OK */

22:40:45  [22ms]  ⏳ PENDING (fallback) - orderId: 1096
22:40:45  [21ms]  ⏳ PENDING (fallback) - orderId: 1056
22:40:46  [12ms]  ⏳ PENDING (fallback) - orderId: 1176
22:40:46  [16ms]  ⏳ PENDING (fallback) - orderId: 1146
  ... (PENDING 응답 지속)

/* HALF_OPEN 전이 시 테스트 요청 → PG 성공 시 CLOSED로 복귀 가능 */

22:34:09  [12ms]  ✅ SUCCESS - orderId: 1025          ← HALF_OPEN에서 성공

/* 주문 중복 시 비즈니스 에러 */

22:34:02  [6ms]   ❌ FAILED - 결제를 시작할 수 없는 주문 상태  ← 이미 PAYMENT_PENDING
```

### Prometheus 메트릭 (테스트 후 스냅샷)

| 메트릭 | 값 | 설명 |
|--------|-----|------|
| `circuitbreaker_state` | **OPEN** | 현재 서킷 상태 - PG 장애 지속으로 OPEN 유지 |
| `failure_rate` | **50.0%** | sliding window 내 실패율 (임계값 50% 도달) |
| `not_permitted_calls_total` | **204건** | 서킷 OPEN으로 차단된 호출 → Fallback PENDING 응답 |
| `calls (successful)` | **1건** | PG 호출 성공 건수 |
| `calls (failed)` | **10건** | PG 호출 실패 건수 (Retry 소진 후) |
| `buffered_calls (failed)` | **5** | 현재 sliding window 내 실패 건수 |
| `buffered_calls (successful)` | **5** | 현재 sliding window 내 성공 건수 |

### k6 터미널 최종 출력

```
========================================
  서킷브레이커 부하 테스트 결과
========================================

📊 요청 결과:
  ✅ PG 즉시 성공:       1건
  ⏳ PENDING (fallback): 204건  ← PG 장애 시 fallback으로 PENDING 유지
  ❌ 실패:              95건

📋 실패 상세:
  ⏱️ PG 타임아웃:        0건
  🔴 서킷 OPEN 차단:     0건  ← fallback이 PENDING 응답 반환 (에러 아님)
  🔌 Connection Refused: 0건
  ❓ 기타 에러:          95건  ← 주문 상태 PAYMENT_PENDING (중복)

⏱️ 응답 시간:
  평균: 20ms
  p95:  24ms
  최대: 470ms

💡 서킷브레이커 + Fallback 동작 분석:
  - PENDING 204건: PG 장애 → Fallback → PENDING 응답 (200 OK)
  - 사용자에게 에러 대신 "처리 대기 중" 응답 → UX 보호
  - 스케줄러가 PG 조회 후 최종 상태 확정 (Eventual Consistency)
========================================
```

> **PENDING Fallback 효과**: PG 장애 시 204건의 요청이 PENDING 응답으로 처리됨.
> 사용자에게 에러 대신 "처리 대기 중" 200 OK를 반환하여 UX를 보호하고,
> 스케줄러가 PG 조회 후 최종 상태(SUCCESS/FAILED)를 확정합니다.

---

## 7. 코드 변경 요약

| 계층 | 파일 | 변경 내용 | 태그 |
|------|------|-----------|------|
| Domain | `UserCoupon.java` | `restore()` 메서드 추가 (USED → AVAILABLE) | 신규 |
| Domain | `CouponService.java` | `restoreUserCoupon(Long userCouponId)` 메서드 추가 | 신규 |
| Infra | `PgPaymentGateway.java` | `requestPaymentFallback`: PENDING 응답 반환 (예외 X), `getTransaction/getTransactionsByOrderFallback`: CoreException 반환 | Resilience |
| Application | `PaymentFacade.java` | requestPayment: PG fallback 시 PENDING 유지, handleCallback/recoverPayment: 확정 FAILED 시 쿠폰 복원, handleNoTransactionFound: TTL 3분 초과 시 FAILED 처리 + 쿠폰 복원 | 변경 |
| Application | `PaymentTransactionService.java` | 즉시 쿠폰 복원 로직 **제거** (PG 결과 미확정이므로) | 제거 |
| Test | `CouponServiceTest.java` | 쿠폰 복원 테스트 3건 (성공/AVAILABLE상태/NOT_FOUND) | 테스트 |
| Test | `PaymentFacadeTest.java` | 콜백 FAILED 쿠폰복원, 스케줄러 FAILED 쿠폰복원, 쿠폰없는 주문, PENDING fallback, TTL 만료 테스트 | 테스트 |
| Test | `PaymentTransactionServiceTest.java` | 결제 실패 상태 변경 테스트 (쿠폰 복원 없이) | 테스트 |

---

## 8. 설계 판단 근거

### 트랜잭션 분리 (TX1 → PG 호출 → TX2)

외부 PG 호출 중 DB 커넥션을 물고 있으면 타임아웃 시 커넥션 풀 고갈 위험.
PG 호출은 트랜잭션 밖에서 실행하고, 결과에 따라 별도 트랜잭션으로 상태 업데이트.

### PaymentTransactionService 분리 (Spring Proxy 문제 해결)

같은 Bean 내 메서드 호출 시 `@Transactional`이 무시됨 (Self-invocation).
트랜잭션 메서드를 별도 `@Service` Bean으로 분리하여 Spring AOP Proxy가 정상 동작하도록 함.

### Fallback에서 PENDING 응답 반환

결제는 "대충 성공" 처리할 수 없지만, "모르면 실패 처리"도 위험.
PG 타임아웃 ≠ 결제 실패이므로, PENDING 상태를 유지하고 스케줄러가 PG 조회 후 확정.
사용자에게는 200 OK + "처리 대기 중"을 반환하여 UX를 보호.

### TTL 기반 PENDING 만료 (3분)

PG에 거래 내역이 없고 3분이 경과한 PENDING 결제건은 FAILED로 처리.
Retry 최대 소요 18초 + PG 콜백 여유를 고려한 값.
PG가 정말로 처리하지 않은 경우를 안전하게 정리하고 쿠폰을 복원.

### 쿠폰 복원은 PG 확정 실패 시점에만

PG 타임아웃 ≠ 결제 실패. PG에서는 성공했는데 응답만 못 받은 경우가 존재.
즉시 복원하면 쿠폰 이중 사용 가능. PG 콜백 또는 스케줄러로 확정된 실패만 복원.

### 복구 스케줄러로 최종 정합성 보장

PENDING 상태로 남은 결제건을 주기적으로 PG에 조회하여 실제 결과 확인.
성공이면 SUCCESS로, 실패면 FAILED + 쿠폰 복원으로 최종 정합성(Eventual Consistency) 달성.

---

## 9. Resilience4j 설정값

```yaml
resilience4j:
  retry:
    instances:
      pgPayment:
        maxAttempts: 3
        waitDuration: 1s
        retryExceptions:
          - feign.FeignException
          - java.io.IOException

  circuitbreaker:
    instances:
      pgPayment:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        failureRateThreshold: 50          # 50% 실패 시 OPEN
        waitDurationInOpenState: 30s      # 30초 후 HALF_OPEN
        permittedNumberOfCallsInHalfOpenState: 3
        minimumNumberOfCalls: 5

spring.cloud.openfeign:
  client:
    config:
      pg-payment-client:
        connectTimeout: 3000             # 3초
        readTimeout: 5000                # 5초
```
