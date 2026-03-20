# 결제·외부연동 테스트 체크리스트

> 근거: `.docs/design/06-payment-implementation-plan.md` §11·§14.  
> `[x]` 완료, `[~]` 부분(문서·소스 정합성 또는 한계 명시), `[ ]` 미구현.

## Phase 0: 의존성

- [x] `CommerceApiContextTest` — 컨텍스트 로드 + Feign·Resilience 핵심 빈 존재

## Phase 1: Timeout

- [x] `PaymentFeignTimeoutPropertiesIntegrationTest` — Feign connect/read 타임아웃 바인딩
- [~] Feign **readTimeout 실경로 E2E** — MockBean이 `PgSimulatorClient`를 대체하여 **지연 응답으로도 Feign 타임아웃을 재현하지 못함**. `PaymentV1ApiE2ETest` 클래스 주석·WireMock/실 PG 필요.

## Phase 2: 트랜잭션 경계

- [x] `PaymentPersistenceServiceIntegrationTest` — PENDING 저장·금액 반영 (= §14 `savePending...afterCommit` 내용)
- [x] `PaymentFacadeRequestPaymentIntegrationTest.requestPayment_afterPersistenceCommit_callsPgClientOutsideTx` — PG 호출 시점에 활성 트랜잭션 없음

## Phase 3: 비동기 결제

### Unit

- [x] `PaymentModelTest` — createPending / markSuccess·Failed·Timeout / 비-PENDING 전이 거부
- [x] `PaymentModelTest.isPending_accordingToStatus_returnsCorrectly`
- [x] `PaymentRequestParamTest` — null 금액, 소수 반올림

### Integration

- [x] `PaymentPersistenceServiceIntegrationTest` — NOT_FOUND, BAD_REQUEST, CONFLICT
- [x] `PaymentFacadeCallbackIntegrationTest` — 성공/실패/금액불일치/멱등/무PENDING/**amount null 성공**
- [x] `PaymentFacadeCallbackIntegrationTest.handleCallback_whenOrderAlreadyPAID_shouldSkipCompletePayment`
- [x] `PaymentFacadeCallbackOrderIntegrationTest.handleCallback_orderOfOperations_pendingFetchedBeforeCompletePayment` — `completePayment` 진입 시점 DB PENDING
- [x] `PaymentFacadeCallbackStockIntegrationTest` — 콜백 시점 재고 부족 → 예외·PENDING 유지 (§11.3)

### E2E

- [x] `PaymentV1ApiE2ETest` — 유효 요청·401·409·404·400(취소)·**PAID 후 재결제 400**·**콜백 JSON 깨짐 400**·PG 예외
- [x] `PaymentV1PaymentCallbackSecretE2ETest` — 시크릿 미제공/오류 시 **401**, 정상 시 200 (`ErrorType.UNAUTHORIZED` = HTTP 401, 문서 403과 불일치 시 운영에서 FORBIDDEN 도입 검토)

## Phase 4: Circuit Breaker

- [x] `PaymentFacadeCircuitBreakerIntegrationTest` — OPEN 스킵·CLOSED 호출·중복 CONFLICT
- [x] `PaymentFacadeCircuitBreakerFailureIntegrationTest` — 연속 실패 시 OPEN 후 PG 미호출
- [~] §14 Phase 4 **리스크 표 전항** (오탐·slow call·4xx CB 집계 등) — WireMock·전용 프로파일 미도입, 핵심 경로만 커버

## Phase 5: Retry

- [x] `PgPaymentRequester` — `@Retry` + `application.yml` `pgRetry`
- [x] `PaymentFacadeRetryIntegrationTest` — 일시 오류 3회·**Feign 503(ServiceUnavailable)**·**Feign 400 비재시도**
- [x] `PaymentResilienceRetryYamlContentTest` — YAML에 exponential backoff·jitter 키 존재

## Phase 6: Fallback

- [x] `PaymentFacadeRequestPaymentIntegrationTest` / `PaymentV1ApiE2ETest` — PG 예외·OPEN 시 200 + PENDING (응답 메시지 필드 없음 → `status` 검증)

## Phase 7: 멱등성

- [x] `PaymentPersistenceConcurrencyIntegrationTest`
- [x] `PaymentV1ApiE2ETest.requestPayment_whenDuplicatePending_shouldReturn409`

## Phase 8: 콜백 미수신 복구

- [x] `PgSimulatorClientReturnTypeTest` — 조회 API 반환 타입 DTO
- [x] `PaymentFacade.recoverPendingFromPgSimulator` + `PaymentRecoverPollIntegrationTest` — PG 조회 SUCCESS/FAILED/null/예외/무PENDING·orderId null

## 보안

- [x] `PaymentFacadeCallbackSecretIntegrationTest` — Facade `verifyCallbackSecret`
- [x] `PaymentV1PaymentCallbackSecretE2ETest` — HTTP 콜백 시크릿 E2E
- [x] `PaymentFacadeCallbackIntegrationTest.handleCallback_whenAmountMismatch_shouldNotCompletePayment`

## 스펙·DTO

- [~] paymentId vs pgTransactionId — 필드 매핑은 코드·`PgPaymentStatusResponse` 주석; **전용 단위 테스트 없음**

## 여전히 범위 밖·미흡 (참고)

- [ ] §11.1 PENDING 저장 **전** DB 장애·프로세스 **PG 호출 전** 크래시 — 자동화 어려움
- [ ] §14 Phase 4 리스크 표 **전부**
- [ ] §11.8 인프라(키 만료·웹훅 화이트리스트) — 과제 스코프 밖
- [ ] `completePayment` **DB 데드락** 등 — 별도 카오스/부하 테스트
