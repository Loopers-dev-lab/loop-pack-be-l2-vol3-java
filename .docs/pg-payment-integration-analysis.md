# PG 결제 연동 설계 분석

## 1. 불확실성 관점에서의 재해석

이 설계에서 외부 시스템(PG)과의 접점은 **세 군데**입니다:

| 접점 | 방향 | 불확실성 |
|------|------|---------|
| `pgClient.requestPayment()` | 우리 → PG | PG가 요청을 받았지만 응답을 돌려주지 못할 수 있음 |
| PG 콜백 (`/callback`) | PG → 우리 | 콜백이 유실되거나, 중복 전송될 수 있음 |
| `pgClient.getPaymentStatus()` | 우리 → PG (폴링) | PG 조회 API가 장애일 수 있음 |

현재 설계는 이 불확실성을 다음과 같이 다루고 있습니다:

- **지연**: RestClient 3초 타임아웃 + Resilience4j 서킷 브레이커 (`slow-call-duration-threshold: 2s`)
- **실패**: Retry(결제 요청 2회, 상태 조회 3회) + fallback 메서드
- **중복 실행**: `isTerminal()` 체크 + `@Version` 낙관적 락
- **성공했지만 응답 유실**: 콜백 미수신 시 폴링 스케줄러가 30초 간격으로 복구

---

## 2. 트랜잭션 경계 검증

현재 설계에서 가장 주목할 구조적 결정은 **외부 호출을 트랜잭션 밖으로 분리한 것**입니다.

```
[Transaction 1 — PaymentFacade.requestPayment()]
  Order 검증 → Payment 생성(REQUESTED) → 이벤트 발행
  → COMMIT

[트랜잭션 외부 — PaymentEventListener (AFTER_COMMIT)]
  PG API 호출

[Transaction 2 — PaymentResultHandler.handlePgAccepted/Rejected()]
  결과 반영
```

### 질문 1: 외부 호출 실패 시 내부 상태는 어떻게 되는가?

PG 호출이 실패하면 fallback이 `accepted=false`를 반환하고, `handlePgRejected()`가 새 트랜잭션에서 Payment → TIMEOUT, Order → PAYMENT_TIMEOUT, 재고 복구를 수행합니다. **내부 상태는 정합적입니다.**

다만, `handlePgRejected()` 자체가 실패하면(DB 장애 등)? → Payment는 REQUESTED 상태로 남고, 폴링 스케줄러가 30초 후 재시도합니다. **복구 경로가 존재합니다.**

### 질문 2: 내부 커밋 이후 외부 호출 실패 시 복구 가능한가?

Transaction 1이 커밋된 후 PG 호출이 실패하는 시나리오입니다.

- Payment는 DB에 REQUESTED로 저장됨 (커밋 완료)
- PG 호출 실패 → fallback → `handlePgRejected()` 호출
- `handlePgRejected()` 성공 시 → Payment TIMEOUT + 재고 복구 (정합적)
- `handlePgRejected()` 실패 시 → REQUESTED 상태로 남음 → **스케줄러가 복구**

**복구 가능합니다.** REQUESTED 상태의 Payment는 항상 스케줄러의 관심 대상이므로, 일시적 장애로 남겨진 건도 최종적으로 처리됩니다.

### 질문 3: 외부 성공 후 내부 실패 시 상태는 어떻게 정합성을 유지하는가?

이것이 이 설계의 **가장 민감한 지점**입니다.

PG가 결제를 성공 처리했는데, 콜백 수신 시 `handleCallback()`의 트랜잭션이 실패하면?

- 콜백 처리 실패 → PG에 200이 아닌 4xx/5xx 응답
- PG가 콜백을 재전송할 수 있음 (PG 구현에 따라 다름)
- PG가 재전송하지 않더라도, **스케줄러가 PG에 상태 조회 → SUCCESS 확인 → 재처리**

**현재 설계에서 PG 성공 + 내부 실패의 최종 정합성은 폴링 스케줄러에 의존합니다.** 스케줄러가 정상 동작하는 한 정합성은 유지됩니다.

---

## 3. 상태 기반 구조 분석

### 내부 상태 (Payment)

```
REQUESTED ──→ SUCCESS
           ──→ FAILED
           ──→ TIMEOUT
```

모든 전이는 `REQUESTED`에서만 가능 (`canTransitTo()`). Terminal 상태에 도달하면 더 이상 변경 불가.

### 외부 상태 (PG)

```
PENDING ──→ SUCCESS
         ──→ FAILED
```

### 두 상태가 어긋날 수 있는 지점

| 시나리오 | 내부 상태 | 외부 상태 | 어긋남? |
|---------|----------|----------|--------|
| 정상 콜백 수신 | SUCCESS/FAILED | SUCCESS/FAILED | 일치 |
| 콜백 유실, 스케줄러 복구 | SUCCESS/FAILED | SUCCESS/FAILED | 일치 (복구됨) |
| PG 성공 + 5분 초과 + PG 조회도 장애 | **TIMEOUT** | **SUCCESS** | **불일치** |
| PG 요청 도달 전 fallback | TIMEOUT | (존재하지 않음) | 정합적 |

**핵심 불일치 지점**: PG에서 실제로 결제가 성공했지만, 우리 쪽 폴링 조회 API까지 장애인 상태로 5분이 경과하면, 우리는 TIMEOUT 처리하고 재고를 복구합니다. 하지만 PG에서는 결제가 성공한 상태입니다.

현재 설계는 이 불일치를 **인식하고 있으며**, 타임아웃 직전에 PG 조회를 한 번 더 수행하여 확률을 최소화합니다. 하지만 PG 조회 API 자체가 장애이면 fallback이 PENDING을 반환하므로, **근본적 해소는 불가능합니다** (결제 취소 API가 없는 이상).

---

## 4. 중복 요청 및 재시도 분석

### 결제 요청 중복 (같은 주문에 두 번 결제 요청)

- **앱 레벨**: `existsByOrderId()` → CONFLICT(409)
- **DB 레벨**: `order_id UNIQUE` 제약

두 요청이 동시에 `existsByOrderId` 체크를 통과하면? → DB UNIQUE가 `DataIntegrityViolationException` → 500. 현재 이 예외를 CONFLICT로 변환하는 핸들러는 없으므로, 동시 중복 시에는 500이 발생합니다. 앱 레벨 체크는 **대부분의 중복을 걸러주지만, 완전한 방어는 아닙니다.**

### 콜백 중복 수신

- `isTerminal()` 체크 → 이미 처리된 건은 무시 (`return false`)
- 동시 콜백 → `@Version` 낙관적 락 → `ObjectOptimisticLockingFailureException` → CONFLICT(409)

**멱등성이 보장됩니다.**

### PG 결제 요청의 재시도 (Resilience4j Retry)

현재 결제 요청 Retry 설정:
- `max-attempts: 2` (원본 1회 + 재시도 1회)
- `retry-exceptions: ConnectException`만

**ConnectException만 재시도하는 것은 의도적입니다.** ConnectException은 요청이 PG에 도달하지 않았음이 확실합니다. 반면 SocketTimeoutException은 요청이 PG에 도달했을 수 있으므로, 재시도하면 **PG 측에서 동일 주문에 대해 두 건의 결제가 생성될 수 있습니다.** 이는 PG가 orderId 기반 멱등성을 보장하지 않는 한 위험합니다.

단, **상태 조회 API는 조회 전용이므로** SocketTimeoutException도 안전하게 재시도할 수 있으며, 현재 설정이 이를 반영하고 있습니다 (`retry-exceptions`에 `SocketTimeoutException` 포함).

---

## 5. 장애 시나리오 분석

### 시나리오 1: AFTER_COMMIT 이벤트 리스너 자체의 예외

`PaymentEventListener.handlePaymentRequest()`에서 `paymentService.findById()`가 실패하면?

- catch 블록이 잡아서 로깅만 수행
- Payment는 REQUESTED + transactionKey 미할당 상태로 남음
- 스케줄러: `transactionKey == null` → 5분 후 TIMEOUT 처리

**데이터 정합성**: 유지됨 (최종적으로 TIMEOUT + 재고 복구)
**리스크**: findById 실패는 DB 장애를 의미할 수 있으며, 이 경우 스케줄러의 복구도 실패할 가능성이 높음. 하지만 이는 시스템 전체 장애이므로 결제 모듈만의 문제는 아님

### 시나리오 2: PG 접수 성공 후 transactionKey 저장 실패

`handlePgAccepted()`의 트랜잭션이 실패하면?

- PG에는 결제 건이 생성됨 (status=PENDING, transactionKey 할당됨)
- 우리 DB에는 transactionKey가 null인 Payment(REQUESTED)가 남음
- 스케줄러: `transactionKey == null` → 5분 후 TIMEOUT 처리
- PG: 콜백을 보내올 수 있음 → 우리 쪽에 해당 transactionKey 매칭 불가 → NOT_FOUND

**데이터 정합성**: **불일치 가능**. PG에서 결제가 성공/실패 처리되지만, 우리는 transactionKey를 모르므로 콜백도 매칭 못 하고, 폴링도 불가. 결국 TIMEOUT 처리됨.
**복구 가능성**: PG 측 결제 취소 API가 없으면 수동 대사 필요

### 시나리오 3: 재고 복구 중 일부 상품이 삭제된 상태

`handleCallback(FAILED)` → `restoreStock()` 시 상품이 soft delete되었다면?

- `productService.findAllByIds()` 구현에 따라 삭제된 상품은 조회 안 될 수 있음
- `quantityByProductId`에는 키가 있지만 `products` 리스트에 없는 경우 → `NullPointerException` 가능

**데이터 정합성**: **트랜잭션 롤백**. handleCallback 전체가 실패하므로 Payment/Order 상태 변경도 안 됨. REQUESTED 상태로 남아 스케줄러가 재시도하지만, 같은 이유로 계속 실패.
**복구 가능성**: 수동 개입 필요 (삭제된 상품 복원 또는 해당 상품 재고 복구 건너뛰기)

### 시나리오 4: 서킷 브레이커 OPEN 상태에서의 신규 결제 요청

서킷이 OPEN이면 PG 호출 없이 즉시 fallback이 실행됩니다.

- `requestPaymentFallback` → `accepted=false` → `handlePgRejected()` → TIMEOUT + 재고 복구
- 결제를 시도조차 하지 않고 실패 처리

**데이터 정합성**: 유지됨
**운영 리스크**: 서킷 OPEN 30초 동안 모든 결제 요청이 즉시 실패 처리됨. 사용자 경험 영향이 크며, 이 기간의 주문은 재결제가 필요. 현재 Order는 PAYMENT_TIMEOUT 상태에서 다시 PENDING_PAYMENT로 돌아가는 전이가 없으므로, **해당 주문은 재결제 불가능**.

### 시나리오 5: 스케줄러 인스턴스 중복 실행 (다중 서버 배포)

`@Scheduled`는 JVM 인스턴스당 1개씩 실행됩니다. 2대 서버라면 2개의 스케줄러가 동시에 같은 REQUESTED Payment 목록을 조회합니다.

- 같은 Payment를 두 스케줄러가 동시 처리 → `@Version` 낙관적 락으로 1건만 성공
- 실패한 쪽은 `OptimisticLockingFailureException` → 개별 건 catch 블록에서 로깅

**데이터 정합성**: 유지됨 (낙관적 락 보호)
**운영 리스크**: PG에 동일 transactionKey로 상태 조회가 2번 발생 (불필요한 부하). 분산 락(ShedLock 등)으로 해소 가능하지만 현재 범위에서는 과한 설계

---

## 6. 현재 구조의 장점과 리스크 정리

### 장점

| 장점 | 근거 |
|------|------|
| 외부 호출이 트랜잭션 밖 | DB 커넥션 점유 최소화. PG 지연이 커넥션 풀 고갈로 이어지지 않음 |
| 콜백 + 폴링 이중 복구 | 콜백 유실 시 자동 복구. 단일 경로 의존 제거 |
| 유령 결제 방지 | 타임아웃 전 PG 조회 1회 추가 — "PG는 SUCCESS인데 우리는 TIMEOUT" 확률 최소화 |
| Retry 범위 제한 | ConnectException만 재시도. 결제 중복 생성 방지 |
| 상태 전이 단방향 | REQUESTED → terminal만 허용. 잘못된 역전이 불가 |

### 리스크

| 리스크 | 발생 조건 | 영향 | 현재 대응 |
|--------|----------|------|----------|
| PG 성공 + 조회 API 동시 장애 + 5분 경과 | PG 전체 장애 5분 이상 | 유령 결제 (PG 성공, 우리 TIMEOUT) | 없음 (결제 취소 API 부재) |
| transactionKey 저장 실패 | handlePgAccepted TX 실패 | 콜백/폴링 매칭 불가 → TIMEOUT | 없음 (수동 대사 필요) |
| 서킷 OPEN 기간의 주문 복구 불가 | PG 장애 30초 이상 | TIMEOUT 처리된 주문 재결제 불가 | OrderStatus에 재결제 전이 없음 |
| 재고 복구 중 삭제된 상품 | 결제 실패 + 상품 삭제 동시 발생 | NPE → 상태 갱신 전체 실패 | 없음 |
| 다중 서버 스케줄러 중복 | 서버 2대 이상 배포 | PG 불필요한 조회 부하 | 낙관적 락으로 정합성은 보호 |

### 대안 선택지

**리스크: 서킷 OPEN 기간의 주문이 영구 사망**

현재 Order 상태 전이가 단방향(`PENDING_PAYMENT → terminal`)이므로, TIMEOUT 처리된 주문은 재결제할 수 없습니다. 선택지:

| 선택지 | 복잡도 | 운영 부담 |
|--------|--------|----------|
| A. `PAYMENT_TIMEOUT → PENDING_PAYMENT` 역전이 허용 | 낮음 (메서드 1개 추가) | 사용자가 재결제 가능. 기존 Payment는 TIMEOUT 상태로 남음 |
| B. 새 주문 생성 유도 (현재 구조 유지) | 없음 | 사용자 UX 부담. 재고가 이미 복구되었으므로 재주문은 가능 |
| C. 서킷 OPEN 시 결제 요청 자체를 거부 (503) | 낮음 | 사용자가 "잠시 후 다시 시도"할 수 있음. 주문이 PENDING_PAYMENT로 유지됨 |

**C가 가장 보수적이면서 안전합니다.** 서킷이 OPEN이면 결제를 시도하지 않고 503을 반환하면, 주문 상태가 PENDING_PAYMENT로 남아 서킷 복구 후 재결제가 가능합니다. 다만 현재 fallback이 "실패 응답 객체"를 반환하는 구조이므로, 서킷 OPEN을 별도로 감지해야 합니다.

**리스크: transactionKey 저장 실패 → PG 건 미아**

| 선택지 | 복잡도 | 운영 부담 |
|--------|--------|----------|
| A. PG 요청 시 orderId를 전달하고, 폴링 시 orderId 기반 조회 사용 | 중간 | transactionKey 없이도 PG 건 추적 가능 |
| B. 현재 구조 유지 + 수동 대사 배치 | 낮음 | 발생 빈도가 극히 낮으므로 수동 대응 가능 |

현재 PG 시뮬레이터는 `GET /api/v1/payments?orderId={orderId}` 엔드포인트를 제공하므로, **A가 기술적으로 가능합니다.** transactionKey가 null인 Payment의 복구 경로를 orderId 기반 조회로 확장하면, 시나리오 2의 리스크를 구조적으로 해소할 수 있습니다.

---

## 요약

이 설계는 **외부 호출을 트랜잭션 밖으로 분리하고, 콜백 + 폴링 이중 복구로 최종 정합성을 추구하는 구조**입니다. 대부분의 실패 시나리오에서 스케줄러가 자동 복구를 수행하며, 낙관적 락으로 동시 처리도 보호합니다.

**가장 큰 구조적 리스크**는 (1) PG 전체 장애 + 5분 경과 시 유령 결제, (2) 서킷 OPEN 기간의 주문이 재결제 불가능한 점입니다. 전자는 결제 취소 API 없이는 근본적으로 해소 불가능하며, 후자는 서킷 OPEN 시 503 반환으로 완화할 수 있습니다.
