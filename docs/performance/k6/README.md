# k6 Order Test Artifacts

## Paths

- Scripts: `k6/scripts/`
- Helper: `k6/lib/order-fixture.js`
- Raw outputs: `docs/performance/k6/results/`
- Latest run id: `docs/performance/k6/results/latest-run-id.txt`

## Included Scenarios

- `order-create`: 주문 생성 부하
- `order-create-cancel`: 주문 생성 후 즉시 취소
- `order-cancel-idempotency`: 취소 재요청 시 `409` 확인
- `payment-cancel-race`: 결제 시작 요청과 주문 취소 요청 동시 경합

## Latest Report

- `docs/performance/k6/payment-pg-resilience-tuning-20260320.md`
- `docs/performance/k6/payment-cancel-race-k6-report-20260320-175026.md`
- `docs/performance/k6/payment-cancel-race-k6-report-20260320-175630-batch5.md`
- `docs/performance/k6/order-ordercancel-k6-report-20260320-000637.md`

## Baseline Reference

- `docs/performance/k6/payment-cancel-race-k6-report-20260320-165632.md`
- `docs/performance/k6/payment-cancel-race-k6-report-20260320-102457.md`

## Related Policy

- `docs/performance/k6/order-cancel-coupon-conflict-policy.md`
