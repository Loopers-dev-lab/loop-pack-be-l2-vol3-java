# K6 Load Test Report (Order / Order Cancel)

- Run ID: `20260319-225359`
- Date (KST): `2026-03-19`
- Target: `http://localhost:8080`
- App profile: `local`

## Scope

- 주문 생성 부하 테스트 (`POST /api/v1/orders`)
- 주문 생성 + 취소 연속 플로우 테스트 (`POST /api/v1/orders` + `PATCH /api/v1/orders/{orderId}/cancel`)
- 주문 취소 멱등성 테스트 (두 번째 취소 `409` 기대)

## Test Assets

- Script helper: `k6/lib/order-fixture.js`
- Scenarios:
  - `k6/scripts/order-create.js`
  - `k6/scripts/order-create-cancel.js`
  - `k6/scripts/order-cancel-idempotency.js`

## Execution Commands

```bash
BASE_URL=http://localhost:8080 VUS=10 DURATION=20s \
  k6 run --summary-export "docs/performance/k6/results/20260319-225359-order-create-summary.json" \
  "k6/scripts/order-create.js" \
  > "docs/performance/k6/results/20260319-225359-order-create.txt"

BASE_URL=http://localhost:8080 VUS=8 DURATION=20s \
  k6 run --summary-export "docs/performance/k6/results/20260319-225359-order-create-cancel-summary.json" \
  "k6/scripts/order-create-cancel.js" \
  > "docs/performance/k6/results/20260319-225359-order-create-cancel.txt"

BASE_URL=http://localhost:8080 VUS=4 ITERATIONS=40 MAX_DURATION=2m \
  k6 run --summary-export "docs/performance/k6/results/20260319-225359-order-cancel-idempotency-summary.json" \
  "k6/scripts/order-cancel-idempotency.js" \
  > "docs/performance/k6/results/20260319-225359-order-cancel-idempotency.txt"
```

## Result Summary

| Scenario | Load Profile | Iterations | HTTP Reqs | Avg Latency | P95 Latency | HTTP Fail Rate | Checks |
|---|---|---:|---:|---:|---:|---:|---:|
| order-create | `10 VUs, 20s` | 818 | 821 | 93.83 ms | 119.51 ms | 0.00% | 100% |
| order-create-cancel | `8 VUs, 20s` | 454 | 911 | 105.23 ms | 128.87 ms | 0.00% | 100% |
| order-cancel-idempotency | `4 VUs, 40 iterations` | 40 | 123 | 103.79 ms | 142.24 ms | 0.00% | 100% |

## Threshold Check

- `order-create`
  - `http_req_duration p(95)<1200`: PASS
  - `http_req_failed rate<0.01`: PASS
  - `order_create_success rate>0.99`: PASS
- `order-create-cancel`
  - `http_req_duration p(95)<1400`: PASS
  - `http_req_failed rate<0.01`: PASS
  - `order_create_success rate>0.99`: PASS
  - `order_cancel_success rate>0.99`: PASS
- `order-cancel-idempotency`
  - `http_req_duration p(95)<1500`: PASS
  - `http_req_failed rate<0.01`: PASS
  - `first_cancel_success rate>0.99`: PASS
  - `second_cancel_conflict rate>0.99`: PASS

## Notes

- 멱등성 시나리오에서 두 번째 취소는 의도적으로 `409`를 기대 응답으로 처리했다.
- 시나리오 fixture는 테스트 전용 회원/상품을 자동 생성해 독립적으로 실행된다.

## Raw Artifacts

- `docs/performance/k6/results/20260319-225359-order-create.txt`
- `docs/performance/k6/results/20260319-225359-order-create-summary.json`
- `docs/performance/k6/results/20260319-225359-order-create-cancel.txt`
- `docs/performance/k6/results/20260319-225359-order-create-cancel-summary.json`
- `docs/performance/k6/results/20260319-225359-order-cancel-idempotency.txt`
- `docs/performance/k6/results/20260319-225359-order-cancel-idempotency-summary.json`
