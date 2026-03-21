# Payment PG Resilience Tuning Log (2026-03-20)

- Date (KST): `2026-03-20`
- Scope: `apps/commerce-api` payment PG request/cancel/query path
- Purpose: 외부 PG 지연/장애 시 내부 응답 보호, 결과 불명확 건 수습, 설정값 근거 기록

## 1) Applied Resilience Policy

Source: `apps/commerce-api/src/main/resources/application.yml`

### Circuit Breaker (all PG channels)

- Instances: `pg-request`, `pg-cancel`, `pg-query`
- `slidingWindowType=COUNT_BASED`
- `slidingWindowSize=20`
- `minimumNumberOfCalls=10`
- `failureRateThreshold=50`
- `slowCallRateThreshold=50`
- `slowCallDurationThreshold=2s`
- `waitDurationInOpenState=10s`
- `permittedNumberOfCallsInHalfOpenState=5`
- `automaticTransitionFromOpenToHalfOpenEnabled=true`

### Retry (connection exception only)

- `pg-request-connection`: `maxAttempts=1`, `waitDuration=150ms`
- `pg-cancel-connection`: `maxAttempts=1`, `waitDuration=150ms`
- `pg-query-connection`: `maxAttempts=2`, `waitDuration=100ms`
- Retry target exception: `com.loopers.infrastructure.payment.PaymentGatewayConnectionException`

Design intent:

- 비연결성 예외(비즈니스 실패, validation, status conflict)는 재시도하지 않는다.
- open 회로에서는 fast-fail로 외부 장애 전파를 억제한다.
- timeout/결과 불명확 건은 동기 경로에서 무리하게 확정하지 않고, reconcile 경로로 수렴시킨다.

## 2) Recovery and Convergence Strategy

- Start path: 결과 불명확(`PaymentRecoveryRequiredException`) 발생 시 내부 결제 상태를 `REQUESTED`로 유지하고 응답을 정상 반환
- Manual recovery API: `POST /api/v1/payments/{orderId}/reconcile`
- Scheduled recovery:
  - 대상: `REQUESTED`, `CANCEL_REQUESTED`, `CANCEL_RECONCILE_REQUIRED`
  - `requested-min-age-ms=15000` 이후 polling 대상 포함

## 3) Verification Executed

### Functional tests

- `./gradlew :apps:commerce-api:test --tests "com.loopers.infrastructure.payment.PaymentGatewayResilienceExecutorTest" --tests "com.loopers.application.payment.PaymentUseCaseTest"`
  - Result: PASS
- `DOCKER_HOST=unix:///Users/anseonghun/.colima/default/docker.sock TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/Users/anseonghun/.colima/default/docker.sock \
  ./gradlew :apps:commerce-api:test --tests "com.loopers.interfaces.api.payment.PaymentApiE2ETest"`
  - Result: PASS (after forcing Colima socket for Testcontainers)
  - 환경이 기본 Docker 소켓 경로를 못 읽는 경우(예: colima context mismatch), 동일 커맨드에서 socket override가 필요합니다.
  - Confirmed:
    - request/cancel retry attempts = 1 (connection failure only)
    - query retry attempts = 2 (connection failure only)
    - circuit breaker opens and fast-fails after failure-rate condition
    - callback 미수신/결과 불명확 결제건을 수동 reconcile 및 polling으로 수습

### Build

- `./gradlew :apps:commerce-api:build -x test`
  - Result: PASS

### k6 load test run

- Run ID: `20260320-003622`
- Raw artifacts:
  - `docs/performance/k6/results/20260320-003622-order-create.txt`
  - `docs/performance/k6/results/20260320-003622-order-create-cancel.txt`
  - `docs/performance/k6/results/20260320-003622-order-cancel-idempotency.txt`

Threshold outcome from run logs:

- `order-create`
  - `p(95)<1200`: PASS (`94.53ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `order_create_success rate>0.99`: PASS (`100.00%`)
- `order-create-cancel`
  - `p(95)<1400`: PASS (`87.08ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `order_create_success rate>0.99`: PASS (`100.00%`)
  - `order_cancel_success rate>0.99`: PASS (`100.00%`)
- `order-cancel-idempotency`
  - `p(95)<1500`: PASS (`91.96ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `first_cancel_success rate>0.99`: PASS (`100.00%`)
  - `second_cancel_conflict rate>0.99`: PASS (`100.00%`)

## 4) Current Decision

- 현재 설정값을 채택한다.
- 근거:
  - connection 전용 retry + circuit breaker 동작을 테스트로 검증했다.
  - callback 미수신/결과 불명확 건이 수동/주기 복구 경로로 수렴한다.
  - 최신 k6 시나리오 3종에서 threshold를 모두 만족했다.

## 5) Next Tuning Iteration Plan

다음 반복에서는 다음 순서로 1개 변수씩 조정한다.

1. `waitDurationInOpenState`: `10s -> 5s/15s`
2. `minimumNumberOfCalls`: `10 -> 20`
3. `failureRateThreshold`: `50 -> 40/60`
4. retry wait duration (`request/cancel 150ms`, `query 100ms`) 미세조정

평가 기준:

- 기능: 결제/취소/복구 관련 E2E 및 resilience 단위 테스트 pass
- 성능: 3개 k6 시나리오 threshold pass 유지
- 안정성: `K6_FAILURE` 0, 비의도 5xx 0

Rollback 기준:

- threshold 실패 1건 이상
- retry 증대로 인한 tail latency 악화 또는 충돌 증가
- 불명확 결제건 미수습 증가

## 6) Cross-Concurrency Follow-up (Payment Start vs Order Cancel)

- Baseline Run ID: `20260320-102457`
- Baseline Report: `docs/performance/k6/payment-cancel-race-k6-report-20260320-102457.md`
- Re-run #1 Run ID: `20260320-165632`
- Re-run #1 Report: `docs/performance/k6/payment-cancel-race-k6-report-20260320-165632.md`
- Re-run #2 Run ID: `20260320-172408`
- Re-run #2 Report: `docs/performance/k6/payment-cancel-race-k6-report-20260320-172408.md`
- Re-run #3 Run ID: `20260320-175026`
- Re-run #3 Report: `docs/performance/k6/payment-cancel-race-k6-report-20260320-175026.md`
- Re-run #4 Run ID: `20260320-175630-batch5`
- Re-run #4 Report: `docs/performance/k6/payment-cancel-race-k6-report-20260320-175630-batch5.md`

핵심 관찰 (baseline):

- race 요청쌍 상태 계약(`cancel:200/409`, `payment-start:201/409/400`)은 100% 충족
- 그러나 후속 수렴 구간에서 `http_req_failed=14.03%`, `race_failure_5xx=79`, `race_unresolved_after_reconcile_count=80` 관측

재실행 결과 #1 (`20260320-165632`):

- `http_req_failed=0.00%`로 목표(`1% 미만`) 달성
- 상태 계약/조회/서버 에러 관련 threshold 모두 PASS
- `race_unresolved_after_reconcile_count=80`은 여전히 유지

재실행 결과 #2 (`20260320-172408`):

- `http_req_failed=0.00%` 유지
- 상태 계약/조회/서버 에러 관련 threshold 모두 PASS
- `race_unresolved_after_reconcile_count` 미관측(카운터 미출력)

재실행 결과 #3 (`20260320-175026`):

- `http_req_failed=0.00%` 유지
- 상태 계약/조회/서버 에러 관련 threshold 모두 PASS
- 데드락 경합이 `500`으로 전파되지 않고 `409` 충돌 응답으로 수렴

재실행 결과 #4 (`20260320-175630-batch5`):

- 동일 시나리오 5회 반복에서 모든 threshold PASS(5/5)
- `http_req_failed`는 모든 런에서 `0.00%`
- `race_unresolved_after_reconcile_count`는 모든 런에서 `0`

해석:

- DB 유니크 충돌은 `DataIntegrityViolationException -> 409` 매핑으로 5xx 전파를 억제했다.
- 락 경합(`CannotAcquireLockException`)은 `409` 매핑으로 계약 상태(`201/409/400`) 안에서 처리했다.
- 취소 주문 + `REQUESTED` 결제는 `CANCEL_RECONCILE_REQUIRED` 수습 상태 전이 보강으로 잔류 관측이 해소됐다.
