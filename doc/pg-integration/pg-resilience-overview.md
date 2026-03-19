# PG 연동 & Resilience 설계

> Round 6 - 외부 PG 시스템 연동, 서킷브레이커, Fallback, 쿠폰 복원 전략
>
> **상세 시각 자료**: [pg-resilience-overview.html](https://htmlpreview.github.io/?https://github.com/move-wook/loop-pack-be-l2-vol3-java/blob/round6/doc/pg-integration/pg-resilience-overview.html)

---

## 1. 전체 결제 아키텍처

**핵심 원칙**: 외부 PG 호출은 DB 트랜잭션 밖에서 실행

```
Client → POST /api/v1/payments → PaymentV1Controller → PaymentFacade

[TX1] PaymentTransactionService.preparePayment()
  ├─ Order: CREATED → PAYMENT_PENDING
  ├─ Payment 생성: REQUESTED → PENDING
  └─ OrderHistory 기록

[No TX] PgPaymentGateway.requestPayment()   ← 외부 PG 호출
  ├─ @Retry(3회, 1초 간격)
  ├─ @CircuitBreaker(실패율 50% → OPEN)
  └─ fallbackMethod → PENDING 응답 반환

[TX2 - PG 성공] PaymentTransactionService.completePayment()
  ├─ Payment: SUCCESS
  └─ Order: PAID

[PENDING 유지 - PG 장애] → 사용자에게 200 OK + "처리 대기 중"
  └─ 스케줄러가 PG 조회 후 최종 상태 확정
```

---

## 2. Resilience4j 설계

| 항목 | 설정 |
|------|------|
| **Retry** | 3회, 1초 간격, FeignException/IOException 대상 |
| **CircuitBreaker** | 실패율 50% → OPEN (30초 유지), slidingWindow 10, HALF_OPEN 3건 |
| **Feign Timeout** | connect 3초, read 5초 |
| **Fallback** | 결제 요청: PENDING 응답 반환 / 조회 API: CoreException |

### 실행 순서

```
요청 → @Retry (바깥) → @CircuitBreaker (안쪽) → 실제 PG 호출
```

- **PG 정상**: Retry → CB(CLOSED) → PG → 200 OK
- **PG 500 에러**: Retry 3회 소진 → CB 실패 기록 → fallback
- **서킷 OPEN**: CallNotPermittedException → retry-exceptions에 없음 → **Retry 스킵 → 즉시 fallback (~20ms)**

---

## 3. Fallback: PENDING 유지 방식

**"모르면 실패 처리하지 않는다"** — PG 타임아웃 ≠ 결제 실패

```java
// PgPaymentGateway.java
private PaymentGatewayResponse requestPaymentFallback(..., Exception e) {
    return new PaymentGatewayResponse(null, "PENDING", "PG 서비스 불안정으로 결제 처리 대기 중");
}

// PaymentFacade.java
if (pgResponse.transactionKey() != null) {
    completePayment(payment, txnKey, "결제 완료");
} else {
    log.info("결제 처리 대기: orderId={}", orderId);  // PENDING 유지 → 스케줄러 위임
}
```

---

## 4. 쿠폰 복원 전략

| 시점 | 복원 여부 | 이유 |
|------|-----------|------|
| PG 장애/타임아웃 (PENDING 유지) | **안 함** | PG에서 실제 처리됐을 수 있음 |
| TTL 3분 초과 (스케줄러) | **복원** | 충분한 대기 후 PG 미처리 확정 |
| PG 콜백 FAILED | **복원** | PG가 확정적으로 실패 통보 |
| 스케줄러 PG 조회 → FAILED | **복원** | PG 조회로 실패 확정 |

---

## 5. 상태 전이

```
[Order]   CREATED → PAYMENT_PENDING → PAID / PAYMENT_FAILED
[Payment] REQUESTED → PENDING → SUCCESS / FAILED
[Coupon]  AVAILABLE → USED → AVAILABLE (확정 실패 시만 복원)
```

---

## 6. k6 부하 테스트 결과

| 항목 | 결과 |
|------|------|
| 총 요청 | 300건 (5 req/s × 60초) |
| PG 즉시 성공 | 1건 |
| **PENDING (Fallback)** | **204건** — 사용자에게 200 OK 반환 |
| 비즈니스 에러 | 95건 (주문 중복) |
| 평균 응답 시간 | **20ms** (fast-fail) |
| p95 응답 시간 | 24ms |
| 최대 응답 시간 | 470ms |

---

## 7. 코드 변경 요약

| 계층 | 파일 | 변경 |
|------|------|------|
| Domain | `UserCoupon.java` | `restore()` 추가 (USED → AVAILABLE) |
| Domain | `CouponService.java` | `restoreUserCoupon()` 추가 |
| Infra | `PgPaymentGateway.java` | fallback: PENDING 응답 반환 |
| Application | `PaymentFacade.java` | PENDING 유지 + TTL 3분 + 쿠폰 복원 |
| Application | `PaymentTransactionService.java` | 즉시 쿠폰 복원 로직 **제거** |
| Test | `PaymentFacadeTest.java` | PENDING fallback, TTL 만료, 쿠폰 복원 테스트 |

---

## 8. 설계 판단 근거

1. **트랜잭션 분리**: PG 호출 중 DB 커넥션 점유 → 타임아웃 시 커넥션 풀 고갈 방지
2. **PaymentTransactionService 분리**: Spring Self-invocation 문제 해결 (@Transactional 동작 보장)
3. **Fallback PENDING**: PG 타임아웃 ≠ 결제 실패. 사용자에게 200 OK 반환, 스케줄러가 확정
4. **TTL 3분**: Retry 최대 18초 + PG 콜백 여유. 미처리 건 안전하게 정리
5. **쿠폰 확정 후 복원**: 즉시 복원 시 이중 사용 위험. PG 확정 실패만 복원
6. **복구 스케줄러**: PENDING 결제건 PG 조회 → Eventual Consistency 달성
