# 결제·외부연동 테스트 체크리스트

> 근거: `.docs/design/06-payment-implementation-plan.md` §14.  
> 구현 클래스/메서드명은 실제 코드 기준으로 갱신한다.

## Phase 0: 의존성

- [x] `CommerceApiContextTest` — 컨텍스트 로드 + Feign·Resilience 핵심 빈 존재

## Phase 1: Timeout

- [x] `PaymentFeignTimeoutPropertiesIntegrationTest` — Feign connect/read 타임아웃 바인딩
- [x] `requestPayment_whenPgTimeout_shouldReturn200WithPendingMessage` (E2E) — `@Disabled` + 사유 (`PaymentV1ApiE2ETest`, MockBean 한계)

## Phase 2: 트랜잭션 경계

- [x] `PaymentPersistenceServiceIntegrationTest` — PENDING 저장·금액 반영
- [x] `PaymentFacadeRequestPaymentIntegrationTest.requestPayment_afterPersistenceCommit_callsPgClientOutsideTx` — PG 호출 시점에 활성 트랜잭션 없음

## Phase 3: 비동기 결제

### Unit

- [x] `PaymentModelTest` — createPending / markSuccess·Failed·Timeout / 비-PENDING 전이 거부
- [x] `PaymentModelTest.isPending_accordingToStatus_returnsCorrectly`
- [x] `PaymentRequestParamTest` — null 금액, 소수 반올림

### Integration

- [x] `PaymentPersistenceServiceIntegrationTest` — NOT_FOUND, BAD_REQUEST, CONFLICT
- [x] `PaymentFacadeCallbackIntegrationTest` — 성공/실패/금액불일치/멱등/무PENDING
- [x] `PaymentFacadeCallbackIntegrationTest.handleCallback_whenOrderAlreadyPAID_shouldSkipCompletePayment` — PAID 후 콜백 시 스킵(멱등)

### E2E

- [x] `PaymentV1ApiE2ETest` — §14 E2E 표 항목

## Phase 4: Circuit Breaker

- [x] `PaymentFacadeCircuitBreakerIntegrationTest` — OPEN 스킵·CLOSED 호출·중복 CONFLICT
- [x] `PaymentFacadeCircuitBreakerFailureIntegrationTest` — 연속 실패 시 OPEN 후 PG 미호출

## Phase 5: Retry

- [x] `PgPaymentRequester` — `@Retry(name = "pgRetry")` + `application.yml` (`pgRetry`)
- [x] `PaymentFacadeRetryIntegrationTest` — 일시 오류 시 재시도 횟수
- [x] `PaymentFacadeRetryIntegrationTest` — `IllegalArgumentException` 등 비재시도 예외는 1회만 호출

## Phase 6: Fallback

- [x] `PaymentFacadeRequestPaymentIntegrationTest` / `PaymentV1ApiE2ETest` — PG 예외·OPEN 시 200 + PENDING (메시지 필드 없음은 API 스펙 한계로 status로 검증)

## Phase 7: 멱등성

- [x] `PaymentPersistenceConcurrencyIntegrationTest`
- [x] `PaymentV1ApiE2ETest.requestPayment_whenDuplicatePending_shouldReturn409`

## Phase 8: 콜백 미수신 복구

- [x] `PgSimulatorClientReturnTypeTest` — 조회 API 반환 타입 DTO 고정
- [x] `recoverOrPoll_*` — `@Disabled` placeholder (`PaymentRecoverPollIntegrationTest`)

## 보안

- [x] `PaymentFacadeCallbackSecretIntegrationTest` — 시크릿 검증
- [x] `PaymentFacadeCallbackIntegrationTest.handleCallback_whenAmountMismatch_shouldNotCompletePayment`

## 스펙·DTO

- [x] checklist 본 문서 + `06-payment-implementation-plan.md` §14 매핑
