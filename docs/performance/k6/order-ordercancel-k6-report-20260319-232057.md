# K6 Load Test Report (Order / Order Cancel, with Coupon + Point)

- Run ID: `20260319-232057`
- Date (KST): `2026-03-19`
- Target: `http://localhost:8080`
- App profile: `local`

## Data Setup (Enhanced)

- 테스트 회원 자동 생성
- 테스트 상품 자동 생성 (주문용)
- 포인트 사용 주문 구성 (`pointAmount=1000`)
- 쿠폰 생성/지급 포함
  - admin API로 쿠폰 생성 (`/api-admin/v1/coupons`)
  - 회원 쿠폰 발급 (`/api/v1/coupons/{couponId}/issue`)
  - VU별 쿠폰 선택 로직 적용 (`K6_COUPON_POOL_SIZE=20`)

## Scenario Configuration

- `order-create`: `VUS=6`, `DURATION=20s` (포인트 사용)
- `order-create-cancel`: `VUS=4`, `DURATION=20s` (쿠폰+포인트 사용)
- `order-cancel-idempotency`: `VUS=4`, `ITERATIONS=40`, `MAX_DURATION=2m` (쿠폰+포인트 사용)

## Result Summary

| Scenario | Iterations | HTTP Reqs | Avg Latency | P95 Latency | HTTP Fail Rate | Check Success |
|---|---:|---:|---:|---:|---:|---:|
| order-create | 505 | 548 | 80.81 ms | 100.30 ms | 0.54% | 99.40% |
| order-create-cancel | 247 | 534 | 81.18 ms | 98.31 ms | 0.56% | 99.38% |
| order-cancel-idempotency | 40 | 157 | 71.85 ms | 93.50 ms | 1.91% | 96.10% |

## Threshold Check

- `order-create`
  - `http_req_duration p(95)<1200`: PASS
  - `http_req_failed rate<0.01`: PASS (`0.54%`)
  - `order_create_success rate>0.99`: PASS (`99.40%`)
- `order-create-cancel`
  - `http_req_duration p(95)<1400`: PASS
  - `http_req_failed rate<0.01`: PASS (`0.56%`)
  - `order_cancel_success rate>0.99`: PASS (`100.00%`)
  - `order_create_success rate>0.99`: FAIL (`98.78%`)
- `order-cancel-idempotency`
  - `http_req_duration p(95)<1500`: PASS
  - `http_req_failed rate<0.01`: FAIL (`1.91%`)
  - `first_cancel_success rate>0.99`: FAIL (`92.50%`)
  - `second_cancel_conflict rate>0.99`: FAIL (`92.50%`)

## Notes

- 실패는 주문 생성 단계에서 일부 발생했고(`order create status is 201` 미충족), 이후 취소 단계 검증(`200`, `409`) 자체는 생성 성공 케이스에서 기대대로 동작했다.
- 이번 런은 쿠폰/포인트가 포함된 현실 데이터 셋으로 재실행한 결과이며, 단순 상품 주문 대비 실패율이 증가했다.

## Raw Artifacts

- `docs/performance/k6/results/20260319-232057-order-create.txt`
- `docs/performance/k6/results/20260319-232057-order-create-summary.json`
- `docs/performance/k6/results/20260319-232057-order-create-cancel.txt`
- `docs/performance/k6/results/20260319-232057-order-create-cancel-summary.json`
- `docs/performance/k6/results/20260319-232057-order-cancel-idempotency.txt`
- `docs/performance/k6/results/20260319-232057-order-cancel-idempotency-summary.json`
