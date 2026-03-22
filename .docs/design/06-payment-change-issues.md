# 결제 변경사항에서 발생할 수 있는 문제점

> 현재 결제 도메인·API·연동 변경분을 기준으로, 발생 가능한 문제를 정리한 문서이다.
> 근거는 코드·설계서(06-payment-implementation-plan.md)를 따른다.

---

## 1. 도메인·상태

### 1.1 PaymentModel 상태 전이 검증 없음

**위치**: `PaymentModel.markSuccess()`, `markFailed()`, `markTimeout()`

**내용**: 세 메서드 모두 `status == PENDING` 여부를 검사하지 않는다.  
예: 이미 `SUCCESS`인 결제에 `markFailed()`를 호출하면 `FAILED`로 덮어쓰여, 정합성이 깨진다.

**발생 시나리오**: 콜백 중복 수신 시 한 번은 성공 처리, 다음에 실패 콜백이 잘못 들어오거나, 폴링/수동 복구 로직에서 이미 SUCCESS인 건에 `markTimeout()`을 호출하는 경우 등.

**권장**: PENDING일 때만 전이하도록 검증 추가.  
예: `if (this.status != PaymentStatus.PENDING) return;` 또는 `throw new IllegalStateException(...)`.

---

### 1.2 결제 금액 소수점 절사

**위치**: `PaymentRequestParam.of(BigDecimal finalAmount, ...)` → `finalAmount.longValue()`

**내용**: `BigDecimal`을 `long`으로 변환할 때 소수 이하가 잘린다.  
예: 10,000.99원 → 10,000으로 PG에 전달되어, PG와 내부 주문 금액이 달라질 수 있다.

**발생 시나리오**: 할인·쿠폰 등으로 `finalAmount`에 소수점이 있는 정책을 쓰는 경우.

**권장**: 06 §2.1·PG 스펙에 맞춰 “최소 통화 단위(전/센트)로 정수만 사용” 등 규칙을 정한 뒤, 그에 맞게 변환(예: `scale` 적용, 반올림 후 정수화). 정수 원만 사용한다면 규칙을 문서에 명시.

---

## 2. 동시성·멱등성

### 2.1 동시 요청 시 PENDING 중복 생성 가능

**위치**: `PaymentPersistenceService.savePendingAndGetRequestParam` — `existsByOrderIdAndStatus(orderId, PENDING)` 후 `save(payment)`.

**내용**: 두 요청이 거의 동시에 들어오면, 둘 다 `existsByOrderIdAndStatus`를 false로 보고 통과한 뒤 각각 PENDING을 저장할 수 있다.  
같은 `orderId`에 PENDING이 2건 생기면, 이후 콜백·폴링 처리 시 “어느 결제를 갱신할지” 모호해지고, 06 §5.2 “같은 주문에 PENDING 1건” 전제가 깨진다.

**발생 시나리오**: 같은 주문에 대해 사용자가 짧은 간격으로 결제 버튼을 두 번 누르거나, 클라이언트/프록시 재시도가 겹치는 경우.

**권장**:  
- 주문 행 락: 해당 `orderId`의 주문을 `SELECT ... FOR UPDATE` 등으로 잡은 뒤 exists → save 수행.  
- 또는 DB 제약: “같은 order_id에 PENDING은 1건만”을 유니크 제약으로 보장(MySQL은 partial unique 미지원이므로, 애플리케이션 락 또는 유니크+상태 설계로 보완).

---

## 3. 흐름·오류 처리

### 3.1 PG 호출 실패 시 5xx만 반환

**위치**: `PaymentFacade.requestPayment` — `pgSimulatorClient.requestPayment(request)` 예외를 잡지 않음.

**내용**: PENDING 저장은 이미 커밋된 상태에서 PG 호출이 타임아웃/5xx 등으로 실패하면 예외가 그대로 전파되어, 클라이언트는 5xx를 받는다.  
내부적으로는 “PENDING + PG 미접수/실패”인데, 사용자는 “결제 실패”로 인식하고 재요청하면 “이미 결제 진행 중”(CONFLICT)이 되어 UX가 나쁘다(06 §11.1, 외부 연동 분석 문서 §2).

**발생 시나리오**: PG 일시 장애, Read Timeout, Connection 실패 등.

**권장**: Phase 6 Fallback 적용 — PG 예외 시 200 + “결제 대기” 등 경량 응답. (이미 PENDING 저장된 상태이므로 Fallback에서는 저장 로직 없이 응답만.)

---

### 3.2 handleCallback 성공 경로의 처리 순서

**위치**: `PaymentFacade.handleCallback` — 성공 시 `orderService.completePayment(orderId)` 호출 후, `findTopByOrderIdOrderByCreatedAtDesc`로 Payment 조회·`markSuccess`·`save`.

**내용**: 먼저 주문을 PAID로 만들고 재고를 차감한 뒤, 그 다음에 Payment를 SUCCESS로 갱신한다.  
이때 Payment 조회/갱신에서 예외가 나면(또는 `filter(PaymentModel::isPending)`으로 걸러져 갱신이 스킵되면), 트랜잭션은 롤백되지만, 설계 의도상 “Payment를 먼저 확정하고 나서 completePayment”가 더 자연스럽고, 예외 시에도 “주문만 PAID, 결제는 PENDING” 같은 불일치 가능성을 줄일 수 있다.

**발생 시나리오**: 콜백 처리 중 Payment 조회/저장 로직 버그, 또는 PENDING이 아닌 결제만 있는 엣지 케이스.

**권장**:  
1) 해당 orderId의 PENDING 결제를 먼저 조회하고, 없으면 멱등 처리(이미 PAID면 200 반환 등).  
2) PENDING이 있으면 `completePayment` → `markSuccess` + `save` 순서 유지.  
(현재도 한 트랜잭션이라 롤백은 되지만, 순서를 명확히 하면 디버깅·재처리 시 혼란이 줄어든다.)

---

## 4. 보안

### 4.1 콜백 엔드포인트 인증·검증 부재

**위치**: `POST /api/v1/payments/callback`, `CustomerWebMvcConfig`에서 인증 제외.

**내용**: 콜백 URL을 아는 누구나 임의의 `orderId`, `success` 등을 보내 호출할 수 있다.  
위조 요청으로 특정 주문을 PAID로 만들면, 재고 차감·결제 완료 처리까지 이루어질 수 있다(06 §11.3 위조/비정상 콜백).

**발생 시나리오**: URL 유출, 내부자 악의, 스캔 등.

**권장**: 06 §11.3에 따라 IP 화이트리스트, PG 시크릿/서명 검증 등으로 콜백 발신 주체를 검증. 검증 실패 시 403 등으로 처리 생략.

---

### 4.2 콜백 수신 시 금액 대조 없음

**위치**: `PaymentFacade.handleCallback`, `OrderService.completePayment`.

**내용**: 06 §11.7에선 “콜백 수신 시 PG 결제 금액과 DB 주문 금액을 반드시 대조”하도록 되어 있다.  
현재는 `completePayment(orderId)`만 호출하고, PG에서 온 금액과 `OrderModel.finalAmount`를 비교하지 않는다.  
PG·클라이언트 조작 시 금액이 다르게 결제될 수 있다.

**발생 시나리오**: 요청 변조, PG/연동 버그로 금액 불일치.

**권장**: 콜백 DTO에 PG 측 결제 금액(또는 동일한 의미의 필드)을 포함시키고, `handleCallback` 또는 `completePayment` 진입 전에 주문의 `finalAmount`와 비교. 불일치 시 completePayment 수행하지 않고 로그·알림 후 수동 검토.

---

## 5. API·연동 스펙

### 5.1 콜백 DTO의 paymentId vs pgTransactionId

**위치**: `PaymentV1Dto.PaymentCallbackRequest`(paymentId 포함), `PaymentV1Controller`에서 `PaymentCallbackParam`으로 넘길 때 `request.paymentId()`를 `pgTransactionId` 인자로 전달.

**내용**: PG 스펙에 “paymentId”와 “transactionId”가 구분되어 있다면, 현재는 paymentId를 그대로 `PaymentModel.pgTransactionId`에 넣고 있어 의미가 뒤섞일 수 있다.  
PG-Simulator가 paymentId만 내려주고 그걸 transactionId로 쓴다면 문제 없을 수 있으나, 실제 PG 연동 시 필드 의미를 설계서·PG 문서와 맞출 필요가 있다.

**권장**: PG-Simulator·실 PG 스펙에 맞춰 “결제 식별자”“PG 트랜잭션 ID”를 구분하고, DTO·도메인 필드명과 매핑을 문서에 명시.

---

### 5.2 PgSimulatorClient 폴링/복구 API 반환 타입

**위치**: `PgSimulatorClient.getPaymentStatus`, `getPaymentsByOrderId` 반환 타입이 `Object`.

**내용**: Phase 8 폴링·수동 복구 시 이 응답을 파싱해 상태를 반영해야 하는데, `Object`면 타입 안전성과 유지보수가 떨어진다.

**권장**: PG-Simulator 응답 스펙에 맞는 DTO를 정의하고, 두 메서드의 반환 타입을 해당 DTO(또는 목록)로 변경. Phase 8 구현 시 그대로 사용.

---

## 6. 요약

| 구분 | 문제 | 심각도(참고) | 대응 |
|------|------|--------------|------|
| 도메인 | PaymentModel 상태 전이 검증 없음 | 중 | PENDING일 때만 전이 |
| 도메인 | 결제 금액 소수점 절사 | 정책 의존 | 통화 단위 규칙 정한 뒤 변환 |
| 동시성 | PENDING 동시 생성 가능 | 중 | 주문 락 또는 유니크 제약 |
| 흐름 | PG 실패 시 5xx만 반환 | 중 | Phase 6 Fallback |
| 흐름 | handleCallback 처리 순서 | 낮음 | PENDING 조회·검증 후 completePayment |
| 보안 | 콜백 인증·검증 부재 | 높음 | IP/시크릿 검증 |
| 보안 | 콜백 금액 대조 없음 | 높음 | PG 금액 vs 주문 금액 검증 |
| 스펙 | paymentId/pgTransactionId 혼용 가능성 | 낮음 | PG 스펙에 맞게 필드 정리 |
| 스펙 | 폴링 API 반환 타입 Object | 낮음 | Phase 8에서 DTO 도입 |

---

## 14. 외부 연동 스킬(SKILL) 기반 검증 테스트

> `skills/analize_external_integration/SKILL.md`의 관점(불확실성, 트랜잭션 경계, 상태, 중복·재시도·멱등, 장애 시나리오)과 본 문서 §1~§5 이슈를 연결한 **테스트 작성 목록**이다.  
> 구현 후 이 절의 테스트가 통과하면 해당 리스크가 **재발하지 않도록** 막는다.  
> 테스트 이름 규칙: `{대상}_{조건}_{예상결과}` (AGENTS.md).

### 14.1 SKILL 관점 ↔ 테스트 역할

| SKILL 절 | 검증 초점 | 이 문서 대응 섹션 |
|----------|-----------|-------------------|
| 1️⃣ 불확실성 | 지연·실패·중복·응답 유실 가정 하의 동작 | §3.1, §3.2, §4 |
| 2️⃣ 트랜잭션 경계 | 외부 호출이 TX 밖인지, 커밋 후 외부 실패·외부 성공 후 내부 실패 시 상태 | §3.1, §3.2 |
| 3️⃣ 상태 기반 | 주문·결제 상태 전이와 불일치 방지 | §1.1, §3.2 |
| 4️⃣ 중복·재시도·멱등 | 동일 요청·콜백 중복 시 이중 처리 방지 | §2.1, §3.2, §4 |
| 5️⃣ 장애 시나리오 | 정합성·복구·롤백 | §1~§5 전반 |

---

### 14.2 §1 도메인·상태

| # | 테스트 이름(제안) | 계층 | 검증하는 이슈 | 성공 시 기대 |
|---|-------------------|------|---------------|--------------|
| T-1.1 | `markFailed_whenStatusIsSuccess_shouldThrowOrNoOp` | Unit (`PaymentModelTest`) | §1.1 | SUCCESS에서 `markFailed`/`markTimeout` 시 상태가 덮어쓰이지 않음(구현 정책: throw 또는 무시) |
| T-1.2 | `markSuccess_whenStatusIsPending_shouldSetSuccessAndPgId` | Unit | §1.1 | PENDING → SUCCESS만 허용 |
| T-1.3 | `markSuccess_whenStatusIsNotPending_shouldThrowOrNoOp` | Unit | §1.1 | 잘못된 전이 차단 |
| T-1.4 | `of_whenFinalAmountHasFraction_shouldMatchPolicy` | Unit (`PaymentRequestParam` 또는 전용 변환기) | §1.2 | 소수 금액 시 PG에 보내는 정수가 **정책(원 단위 절사 vs 최소 단위)**과 일치; 정책 위반 시 테스트가 실패해 구현을 강제 |

---

### 14.3 §2 동시성·멱등성

| # | 테스트 이름(제안) | 계층 | 검증하는 이슈 | 성공 시 기대 |
|---|-------------------|------|---------------|--------------|
| T-2.1 | `savePending_secondRequestWithSameOrderWhilePending_shouldConflict` | Integration (`PaymentPersistenceService` + 실 DB) | §2.1 (순차) | 첫 요청으로 PENDING 저장 후, 동일 `orderId`로 두 번째 요청 시 `CONFLICT` |
| T-2.2 | `requestPayment_concurrentSameOrder_shouldAllowAtMostOnePending` | Integration (선택: `CountDownLatch` + 2 스레드) | §2.1 (동시) | 동시에 두 요청 시 `payment` 테이블에 해당 `orderId`의 PENDING이 **1건만** 존재(락·유니크 제약 도입 후 필수) |
| T-2.3 | `completePayment_whenAlreadyPaid_shouldBeIdempotent` | Integration (`OrderServiceIntegrationTest` 등) | SKILL 4, §3.2 | 이미 PAID인 주문에 `completePayment` 재호출 시 재고 이중 차감 없음, 예외 없음 |

---

### 14.4 §3 흐름·오류 처리 (외부 호출 + TX)

| # | 테스트 이름(제안) | 계층 | 검증하는 이슈 | 성공 시 기대 |
|---|-------------------|------|---------------|--------------|
| T-3.1 | `requestPayment_whenPgThrowsAfterPendingCommitted_shouldPersistPending` | Integration (`PaymentFacade` + `PgSimulatorClient` Mock/WireMock) | SKILL 2, §3.1 | PG 예외 후에도 DB에 PENDING 결제가 남음 |
| T-3.2 | `requestPayment_whenPgThrows_shouldReturnExpectedHttpStatus` | E2E (`MockMvc` + PG 스텁) | §3.1 | **현재 구현**: 5xx 전파 검증. **Fallback 도입 후**: 200 + “결제 대기” 본문 검증으로 테스트 교체·추가 |
| T-3.3 | `handleCallback_success_thenDuplicateCallback_shouldNotDoubleDecreaseStock` | Integration 또는 E2E | SKILL 1·4, §3.2 | 성공 콜백 2회 시 재고·주문 상태가 멱등 |
| T-3.4 | `handleCallback_failure_shouldLeaveOrderOrderedAndPaymentFailed` | Integration | SKILL 3, §3.2 | `success=false` 시 주문 ORDERED 유지, 결제 FAILED |

---

### 14.5 §4 보안 (구현 시 필수)

| # | 테스트 이름(제안) | 계층 | 검증하는 이슈 | 성공 시 기대 |
|---|-------------------|------|---------------|--------------|
| T-4.1 | `paymentCallback_withoutValidSecret_shouldReturnForbidden` | E2E | §4.1 | 시크릿/서명 검증 도입 후, 위조 콜백은 403, 주문·결제 상태 불변 |
| T-4.2 | `paymentCallback_whenAmountMismatch_shouldNotCompletePayment` | Integration 또는 E2E | §4.2 | 콜백 금액 ≠ `OrderModel.finalAmount` 시 `completePayment` 미실행, PAID 미전이 |

---

### 14.6 §5 API·연동 스펙 (Phase 8 연계)

| # | 테스트 이름(제안) | 계층 | 검증하는 이슈 | 성공 시 기대 |
|---|-------------------|------|---------------|--------------|
| T-5.1 | `getPaymentsByOrderId_shouldDeserializeToExpectedDto` | Unit 또는 Contract | §5.2 | PG 응답 DTO 도입 후, 역직렬화·필드 매핑이 스펙과 일치 |
| T-5.2 | `paymentCallback_mapsPaymentIdToPgTransactionId_perSimulatorSpec` | Unit (Mapper) 또는 E2E | §5.1 | PG-Simulator 필드 정의와 `PaymentCallbackParam`·`pgTransactionId` 매핑이 문서와 동일 |

---

### 14.7 SKILL 5 장애 시나리오 최소 3건 (통합·E2E에서 반드시 커버)

아래 3가지는 SKILL 5️⃣ “실패 흐름 우선”에 해당하는 **최소 세트**이다. T-3.1·T-3.3·T-3.4와 중복되면 하나의 시나리오로 묶어도 된다.

| 시나리오 | 테스트 이름(제안) | 검증 포인트 |
|----------|-------------------|-------------|
| **A. PG 타임아웃/실패 후 내부 상태** | T-3.1, T-3.2 | 데이터 정합성: PENDING 존재. 복구: Fallback 또는 폴링 전제 시 별도 T-8.x 추가 |
| **B. 콜백 중복** | T-3.3 | 상태 불일치 방지: 이중 재고 차감 없음 |
| **C. 콜백 실패(비즈니스)** | T-3.4 | 주문은 ORDERED, 결제 FAILED |

추가 권장(재고·TX):

| 시나리오 | 테스트 이름(제안) | 검증 포인트 |
|----------|-------------------|-------------|
| **D. 성공 콜백인데 재고 부족** | `handleCallback_success_whenStockInsufficient_shouldRollbackOrderAndPayment` | SKILL 2, 06 §11.3 | 단일 TX 롤백으로 주문 PAID 미전이, 결제 SUCCESS 미반영(또는 정책에 맞는 실패 상태) |

---

### 14.8 구현 순서 제안

1. **Unit**: T-1.1 ~ T-1.4 (도메인·VO 규칙 고정)  
2. **Integration**: T-2.1, T-2.3, T-3.1, T-3.3, T-3.4, (재고 시) D  
3. **E2E**: T-3.2, T-4.1, T-4.2 (보안·HTTP 계약)  
4. **동시성**: T-2.2는 락/유니크 적용 **직후**에 추가 (미적용 시 flaky 가능)

---

*기준: 현재 결제 관련 변경 파일(OrderModel, PaymentModel/Status/Repository, PaymentFacade, PaymentPersistenceService, PgSimulatorClient, Controller, DTO, application.yml, CustomerWebMvcConfig 등) 및 06-payment-implementation-plan.md, 06-payment-external-integration-analysis.md.*
