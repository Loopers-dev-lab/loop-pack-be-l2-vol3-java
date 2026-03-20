# K6 Load Test Report (Order / Order Cancel, Post Point Init Concurrency Fix)

- Run ID: `20260320-000637`
- Date (KST): `2026-03-20`
- Target: `http://localhost:8080`
- App profile: `local`

## Change Under Test

- `PointBalanceRepositoryImpl.initializeIfAbsent`를 `find -> save -> catch` 방식에서 DB-native upsert 호출로 변경했다.
  - before: `findByMemberId` 후 `save` 시도 (insert 경쟁 구간 존재)
  - after: `PointBalanceJpaRepository.initializeIfAbsent` (`INSERT ... ON DUPLICATE KEY UPDATE`)
- 목적: 주문 생성 초기 동시 구간에서 발생하던 `point_balances.uk_point_balances_member_id` 중복키 500 제거.

## Scenario Configuration

- `order-create`: `VUS=6`, `DURATION=20s` (포인트 사용)
- `order-create-cancel`: `VUS=4`, `DURATION=20s` (쿠폰+포인트 사용)
- `order-cancel-idempotency`: `VUS=4`, `ITERATIONS=40`, `MAX_DURATION=2m` (쿠폰+포인트 사용)

## Result Summary

| Scenario | Iterations | HTTP Reqs | Avg Latency | P95 Latency | HTTP Fail Rate | Key Checks |
|---|---:|---:|---:|---:|---:|---:|
| order-create | 493 | 536 | 89.77 ms | 116.88 ms | 0.00% (0/536) | order_create_success 100.00% (493/493) |
| order-create-cancel | 244 | 531 | 84.35 ms | 103.68 ms | 0.00% (0/531) | order_create_success 100.00% (244/244), order_cancel_success 100.00% (244/244) |
| order-cancel-idempotency | 40 | 163 | 86.49 ms | 145.34 ms | 0.00% (0/163) | first_cancel_success 100.00% (40/40), second_cancel_conflict 100.00% (40/40) |

## Threshold Check

- `order-create`
  - `http_req_duration p(95)<1200`: PASS (`116.88 ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `order_create_success rate>0.99`: PASS (`100.00%`)
- `order-create-cancel`
  - `http_req_duration p(95)<1400`: PASS (`103.68 ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `order_create_success rate>0.99`: PASS (`100.00%`)
  - `order_cancel_success rate>0.99`: PASS (`100.00%`)
- `order-cancel-idempotency`
  - `http_req_duration p(95)<1500`: PASS (`145.34 ms`)
  - `http_req_failed rate<0.01`: PASS (`0.00%`)
  - `first_cancel_success rate>0.99`: PASS (`100.00%`)
  - `second_cancel_conflict rate>0.99`: PASS (`100.00%`)

## Failure Breakdown

- `K6_FAILURE` 로그: 3개 시나리오 모두 `0`건
- `order_create status != 201` 실패: 3개 시나리오 모두 `0`건
- 결과적으로 `order_create_failure_5xx`는 관측값 기준 `0`으로 수렴했다.

## Server Log Correlation

검증 대상 로그: `/tmp/loopers-commerce-api-k6.log`

- `uk_point_balances_member_id` 매치: `0`건
- `Internal Server Error` / `could not execute statement` 매치: `0`건
- `CoreException : 사용 취소할 수 없는 쿠폰입니다.` 매치: `40`건

위 40건은 `order-cancel-idempotency`에서 의도한 second cancel(`409`) 검증에 대응한다.
정책/응답코드는 `docs/performance/k6/order-cancel-coupon-conflict-policy.md`를 기준으로 해석한다.

## Conclusion

- 포인트 초기화 동시성 보강 이후, 이전 런의 주문 생성 500 이슈는 재현되지 않았다.
- 사용자 요구사항 기준으로 `order_create_failure_5xx=0`을 달성했다.
- idempotency 시나리오 threshold 3종(`http_req_failed`, `first_cancel_success`, `second_cancel_conflict`)도 모두 PASS로 회복되었다.

## Raw Artifacts

- `docs/performance/k6/results/20260320-000637-order-create.txt`
- `docs/performance/k6/results/20260320-000637-order-create-summary.json`
- `docs/performance/k6/results/20260320-000637-order-create-cancel.txt`
- `docs/performance/k6/results/20260320-000637-order-create-cancel-summary.json`
- `docs/performance/k6/results/20260320-000637-order-cancel-idempotency.txt`
- `docs/performance/k6/results/20260320-000637-order-cancel-idempotency-summary.json`
