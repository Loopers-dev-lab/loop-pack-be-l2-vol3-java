# K6 Cross-Concurrency Report (Payment Start vs Order Cancel)

- Run ID: `20260320-102457`
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

- `http_req_duration p(95)<1800`: PASS (`157.04ms`)
- `race_status_contract rate>0.99`: PASS (`100.00%`)
- `race_no_server_error rate>0.99`: PASS (`100.00%`)
- `race_order_query_success rate>0.99`: PASS (`100.00%`)
- `race_payment_query_success rate>0.99`: PASS (`100.00%`)
- `http_req_failed rate<0.02`: FAIL (`14.03%`)

## Core Metrics

- Iterations: `80`
- HTTP Requests: `563`
- HTTP avg latency: `107.54ms`
- HTTP p95 latency: `157.04ms`
- HTTP failed rate: `14.03%` (`79/563`)

Custom metrics:

- `race_status_contract`: `80/80` (100.00%)
- `race_no_server_error`: `80/80` (100.00%)
- `race_cancel_applied`: `80/80` (100.00%)
- `race_payment_created_count`: `79`
- `race_payment_conflict_count`: `1`
- `race_failure_5xx`: `79`
- `race_unresolved_after_reconcile_count`: `80`

## Findings

1. race 요청쌍 자체(`order cancel` vs `payment start`)의 상태 계약은 100% 충족했다.
2. 그러나 후속 구간에서 실패가 집중됐다.
   - `race_failure_5xx=79`와 `http_req_failed=14.03%`가 관측됐다.
   - 동일 런에서 `race_unresolved_after_reconcile_count=80`으로, 취소된 주문의 결제가 `REQUESTED`로 남는 케이스가 지속 관측됐다.
3. 결론적으로, 교차 요청 진입 자체보다 **race 이후 reconcile 수렴 경로**가 현재 취약 구간이다.

## Interpretation Scope

- 본 시나리오는 "payment 진행 스레드 vs cancel 스레드" 교차 구간을 재현하고, 후속 조회/수렴까지 관측하도록 구성했다.
- 포인트/재고의 정확한 잔액/수량 검증은 전용 조회 API 부재로 이번 k6에서는 정량 검증 범위에서 제외했다.

## Follow-up Actions

1. `reconcile` 경로의 5xx 원인 분해(예외 타입/분기) 및 4xx/정상 수렴으로 계약 정리
2. 취소 주문의 결제 `REQUESTED` 잔류 케이스에 대한 상태 전이 보강
3. 같은 시나리오 재실행으로 `http_req_failed`를 `1% 미만`으로 회복 확인

## Artifacts

- `docs/performance/k6/results/20260320-102457-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-102457-payment-cancel-race-summary.json`
