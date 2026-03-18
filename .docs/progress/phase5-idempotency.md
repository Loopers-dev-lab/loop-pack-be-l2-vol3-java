# Phase 5: 멱등성 (Idempotency) — 중복 결제 방지

## 학습 목표
- "같은 요청을 N번 보내도 결과가 1번과 같다"를 보장하는 설계를 한다
- 재시도가 안전하려면 멱등성이 전제 조건임을 이해한다
- 동시 요청에 대한 DB 레벨 방어를 구현한다

## 멱등성이 없으면 재시도가 위험한 이유

```
시나리오: Read Timeout 후 자동 재시도 (Phase 4의 Retry)

1차 시도: Client → PG 결제 요청 → PG 처리 성공 → 응답 전송 중 타임아웃
2차 시도: Client → PG 결제 요청 → PG가 새 트랜잭션으로 처리 → 성공

결과: 같은 주문에 대해 PG에서 2번 결제됨 → 이중 결제!
```

이것이 Phase 4(Retry)가 Phase 5(멱등성)를 전제로 하는 이유다.

## 구현: 3중 방어

### 1차 방어: Order 상태 검증 (도메인 규칙)
```java
// Order.startPayment()
if (this.status != OrderStatus.ORDERED) {
    throw new CoreException(ErrorType.BAD_REQUEST,
        "결제를 시작할 수 없는 상태입니다. 현재 상태: " + this.status);
}
```
- 이미 PAYMENT_PENDING인 Order에 대해 다시 `startPayment()` 호출 시 예외
- 하지만 동시 요청 시 두 트랜잭션이 둘 다 ORDERED를 읽을 수 있음 → 불충분

### 2차 방어: 활성 결제 존재 여부 확인 (Application 계층)
```java
boolean hasActivePayment = paymentDomainService.getByOrderId(orderId).stream()
    .anyMatch(p -> p.getStatus() == PaymentStatus.PENDING
        || p.getStatus() == PaymentStatus.IN_PROGRESS);
if (hasActivePayment) {
    throw new CoreException(ErrorType.CONFLICT, "이미 진행 중인 결제가 있습니다.");
}
```
- PENDING 또는 IN_PROGRESS 상태의 결제가 이미 있으면 CONFLICT
- 하지만 동시 요청 시 둘 다 "없음"으로 판단할 수 있음 → 불충분

### 3차 방어: 비관적 락 (DB 레벨)
```java
Order order = orderDomainService.getByIdAndUserIdForUpdate(orderId, userId);
// SELECT ... FOR UPDATE → 같은 Order에 대한 동시 접근 직렬화
```
- `SELECT ... FOR UPDATE`로 Order 행에 락을 걸어 동시 요청 직렬화
- 두 번째 요청은 첫 번째 트랜잭션이 커밋될 때까지 대기
- 커밋 후 두 번째 요청이 실행되면, 이미 PAYMENT_PENDING 상태이므로 1차/2차 방어에서 차단

```
요청A: SELECT FOR UPDATE (락 획득) → Payment 생성 → COMMIT (락 해제)
요청B: SELECT FOR UPDATE (대기...) → 락 획득 → "이미 활성 결제 있음" → CONFLICT
```

## 비관적 락 vs UNIQUE 제약조건

| 방법 | 장점 | 단점 |
|---|---|---|
| **비관적 락 (채택)** | 기존 코드 변경 최소, 복잡한 조건 가능 | 락 대기 시간 발생 |
| UNIQUE 제약조건 | DB 레벨 보장, 락 불필요 | (orderId, status) 조합이 복잡, FAILED 후 재결제 어려움 |

비관적 락을 선택한 이유:
1. PENDING/IN_PROGRESS 상태만 체크하는 복잡한 조건을 코드로 표현 가능
2. FAILED 후 같은 주문에 대해 새 결제를 시작할 수 있어야 함
3. 결제 요청은 빈번하지 않아 락 대기 비용이 낮음

## 콜백 멱등성 (이미 구현됨)

```java
// PaymentTransactionHelper.applyPaymentResult()
if (payment.getStatus() == PaymentStatus.PAID || payment.getStatus() == PaymentStatus.FAILED) {
    return payment; // 이미 최종 상태 → 무시
}
```
- PG 콜백이 중복으로 들어와도 안전
- sync API를 여러 번 호출해도 안전

## 테스트 결과

| 테스트 | 결과 |
|---|---|
| 같은 orderId로 결제 2번 → CONFLICT | ✅ |
| 이전 결제 FAILED 후 새 결제 가능 | ✅ |
| 중복 콜백 처리 → 멱등 | ✅ (Phase 2에서 구현) |
| 기존 테스트 전부 통과 | ✅ |
