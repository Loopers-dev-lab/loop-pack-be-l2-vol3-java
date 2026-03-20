# K6 Cross-Concurrency Report (Payment Start vs Order Cancel)

- Run ID: `20260320-175026`
- Date (KST): `2026-03-20`
- Target: `http://localhost:8080`
- Scenario script: `k6/scripts/payment-cancel-race.js`

## Goal

주문 생성 직후 같은 주문에 대해 아래 두 요청을 동시 발생시켜 교차 동시성 구간을 검증한다.

- `PATCH /api/v1/orders/{orderId}/cancel`
- `POST /api/v1/payments`

검증 포인트:

- race 요청쌍의 상태 계약(허용 상태) 유지 여부
- race 자체에서 서버 에러(5xx) 발생 여부
- race 이후 reconcile/조회 수렴 여부

## Scenario Configuration

- Executor: `shared-iterations`
- `VUS=8`
- `ITERATIONS=80`
- `MAX_DURATION=2m`
- Fixture: 쿠폰 + 포인트 사용 주문 생성 후 race 수행

## Threshold Result

- `http_req_duration p(95)<1800`: PASS (`263.72ms`)
- `race_status_contract rate>0.99`: PASS (`100.00%`)
- `race_no_server_error rate>0.99`: PASS (`100.00%`)
- `race_order_query_success rate>0.99`: PASS (`100.00%`)
- `race_payment_query_success rate>0.99`: PASS (`100.00%`)
- `http_req_failed rate<0.02`: PASS (`0.00%`)

## Core Metrics

- Iterations: `80`
- HTTP Requests: `523`
- HTTP avg latency: `134.90ms`
- HTTP p95 latency: `263.72ms`
- HTTP failed rate: `0.00%` (`0/523`)

Custom metrics:

- `race_status_contract`: `80/80` (100.00%)
- `race_no_server_error`: `80/80` (100.00%)
- `race_cancel_applied`: `80/80` (100.00%)
- `race_payment_created_count`: `71`
- `race_payment_conflict_count`: `9`
- `race_unresolved_after_reconcile_count`: not observed in this run (counter not emitted)

## Findings

1. 데드락 경합이 있더라도 race 요청쌍에서 `500`이 더 이상 관측되지 않았고 `http_req_failed=0.00%`를 유지했다.
2. 상태 계약 및 no-server-error threshold가 모두 PASS로 복구되었다.
3. 취소 주문의 미수습 결제 상태(`REQUESTED/SUCCEEDED`)는 본 런에서 직접 관측되지 않았다.

## Implemented Convergence Policy Changes

1. `ApiControllerAdvice`:
   - `DataIntegrityViolationException`을 중복키 위반인 경우에만 `409(CONFLICT)`로 매핑
   - 중복키가 아닌 무결성 예외는 `500(INTERNAL_ERROR)`로 유지
2. `ApiControllerAdvice`:
   - `CannotAcquireLockException`(deadlock/lock 경합)을 `409(CONFLICT)`로 매핑

## Artifacts

- `docs/performance/k6/results/20260320-175026-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175026-payment-cancel-race-summary.json`
