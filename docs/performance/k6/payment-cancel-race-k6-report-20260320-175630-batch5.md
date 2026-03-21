# K6 Cross-Concurrency Stability Report (Payment Start vs Order Cancel, 5 Runs)

- Batch ID: `20260320-175630-batch5`
- Date (KST): `2026-03-20`
- Target: `http://localhost:8080`
- Scenario script: `k6/scripts/payment-cancel-race.js`
- Purpose: 동일 시나리오 5회 반복으로 분산(플레이크) 여부 확인

## Repeated Run Summary

| Run ID | `http_req_failed` | `race_status_contract` | `race_no_server_error` | `http_req_duration p(95)` | Result |
|---|---:|---:|---:|---:|---|
| `20260320-175630-r1` | `0.00%` | `100.00%` | `100.00%` | `256.69ms` | PASS |
| `20260320-175641-r2` | `0.00%` | `100.00%` | `100.00%` | `255.19ms` | PASS |
| `20260320-175652-r3` | `0.00%` | `100.00%` | `100.00%` | `255.14ms` | PASS |
| `20260320-175704-r4` | `0.00%` | `100.00%` | `100.00%` | `258.54ms` | PASS |
| `20260320-175715-r5` | `0.00%` | `100.00%` | `100.00%` | `265.40ms` | PASS |

## Aggregate Metrics (5 Runs)

- Threshold pass ratio: `5/5` (`100%`)
- `http_req_failed` average: `0.00%`
- `race_status_contract` average: `100.00%`
- `race_no_server_error` average: `100.00%`
- `http_req_duration p(95)`: avg `258.19ms`, min `255.14ms`, max `265.40ms`
- `race_payment_created_count` total: `374`
- `race_payment_conflict_count` total: `26`
- `race_unresolved_after_reconcile_count` total: `0`

## Findings

1. 5회 반복 실행에서 임계치 실패가 재현되지 않아 해당 시나리오 기준 플레이크 신호는 관측되지 않았다.
2. 동시성 경합은 `201/409/400` 계약 범위로 수렴했고, race 구간 5xx는 관측되지 않았다.
3. 취소 주문의 결제 미수습(`REQUESTED/SUCCEEDED`) 카운터는 전 실행에서 0으로 유지되었다.

## Artifacts

- `docs/performance/k6/results/20260320-175630-r1-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175630-r1-payment-cancel-race-summary.json`
- `docs/performance/k6/results/20260320-175641-r2-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175641-r2-payment-cancel-race-summary.json`
- `docs/performance/k6/results/20260320-175652-r3-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175652-r3-payment-cancel-race-summary.json`
- `docs/performance/k6/results/20260320-175704-r4-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175704-r4-payment-cancel-race-summary.json`
- `docs/performance/k6/results/20260320-175715-r5-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-175715-r5-payment-cancel-race-summary.json`
