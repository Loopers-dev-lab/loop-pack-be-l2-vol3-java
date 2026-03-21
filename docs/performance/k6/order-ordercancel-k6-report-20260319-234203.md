# K6 Load Test Report (Order / Order Cancel, with Coupon + Point, Instrumented)

- Run ID: `20260319-234203`
- Date (KST): `2026-03-19`
- Target: `http://localhost:8080`
- App profile: `local`

## Scope

- 쿠폰/포인트가 포함된 주문/주문취소 시나리오를 재실행했다.
- 실패를 분해하기 위해 `K6_FAILURE` 구조화 로그와 `order_create_failure_*` 커스텀 메트릭을 사용했다.
- `X-K6-Run-Id`, `X-K6-Scenario`, `X-K6-Vu`, `X-K6-Iter`, `X-Request-Id` 헤더를 함께 전송해 서버 로그와 상관분석했다.

## Scenario Configuration

- `order-create`: `VUS=6`, `DURATION=20s` (포인트 사용)
- `order-create-cancel`: `VUS=4`, `DURATION=20s` (쿠폰+포인트 사용)
- `order-cancel-idempotency`: `VUS=4`, `ITERATIONS=40`, `MAX_DURATION=2m` (쿠폰+포인트 사용)

## Result Summary

| Scenario | Iterations | HTTP Reqs | Avg Latency | P95 Latency | HTTP Fail Rate | Key Checks |
|---|---:|---:|---:|---:|---:|---:|
| order-create | 508 | 551 | 80.90 ms | 100.83 ms | 0.90% (5/551) | order_create_success 99.01% (503/508) |
| order-create-cancel | 265 | 570 | 78.17 ms | 87.73 ms | 0.52% (3/570) | order_create_success 98.86% (262/265), order_cancel_success 100.00% (262/262) |
| order-cancel-idempotency | 40 | 157 | 73.99 ms | 99.13 ms | 1.91% (3/157) | first_cancel_success 92.50% (37/40), second_cancel_conflict 92.50% (37/40) |

## Threshold Check

- `order-create`
  - `http_req_duration p(95)<1200`: PASS (`100.83 ms`)
  - `http_req_failed rate<0.01`: PASS (`0.90%`)
  - `order_create_success rate>0.99`: PASS (`99.01%`)
- `order-create-cancel`
  - `http_req_duration p(95)<1400`: PASS (`87.73 ms`)
  - `http_req_failed rate<0.01`: PASS (`0.52%`)
  - `order_cancel_success rate>0.99`: PASS (`100.00%`)
  - `order_create_success rate>0.99`: FAIL (`98.86%`)
- `order-cancel-idempotency`
  - `http_req_duration p(95)<1500`: PASS (`99.13 ms`)
  - `http_req_failed rate<0.01`: FAIL (`1.91%`)
  - `first_cancel_success rate>0.99`: FAIL (`92.50%`)
  - `second_cancel_conflict rate>0.99`: FAIL (`92.50%`)

## Failure Breakdown (Instrumentation)

- `K6_FAILURE` 발생 수
  - `order-create`: `5`건
  - `order-create-cancel`: `3`건
  - `order-cancel-idempotency`: `3`건
- 모든 `K6_FAILURE`는 `create_order` 단계의 `500`으로 기록되었다.
- 실패는 각 시나리오에서 거의 모두 `iter=0` (초기 동시 구간)에서 발생했다.

예시 (`docs/performance/k6/results/20260319-234203-order-create.txt`):

```text
K6_FAILURE {"type":"K6_FAILURE","runTag":"19234203","scenario":"order_create_point","stage":"create_order","loginId":"k619234203ind8n1fo","vu":6,"iter":0,"status":500,"errorCode":"Internal Server Error","message":"일시적인 오류가 발생했습니다.","orderId":null,"pointAmount":1000,"couponUsed":false}
```

예시 (`docs/performance/k6/results/20260319-234203-order-create-cancel.txt`):

```text
K6_FAILURE {"type":"K6_FAILURE","runTag":"19234203","scenario":"order_create_cancel_flow","stage":"create_order","loginId":"k619234203inulf3qu","vu":1,"iter":0,"status":500,"errorCode":"Internal Server Error","message":"일시적인 오류가 발생했습니다.","orderId":null,"pointAmount":1000,"couponId":"91851c2b-180a-4517-9bab-012477d1fbd1"}
```

예시 (`docs/performance/k6/results/20260319-234203-order-cancel-idempotency.txt`):

```text
K6_FAILURE {"type":"K6_FAILURE","runTag":"19234203","scenario":"order_cancel_idempotency","stage":"create_order","loginId":"k619234203iobqmjuv","vu":2,"iter":0,"status":500,"errorCode":"Internal Server Error","message":"일시적인 오류가 발생했습니다.","orderId":null,"pointAmount":1000,"couponId":"0381272a-a8ec-4d9a-bb88-5aeea24da973"}
```

## Server Log Correlation

`/tmp/loopers-commerce-api-k6.log`에서 동일 회원 ID 기준으로 아래 예외가 반복 확인되었다.

- `Duplicate entry 'k619234203ind8n1fo' for key 'point_balances.uk_point_balances_member_id'`
- `Duplicate entry 'k619234203inulf3qu' for key 'point_balances.uk_point_balances_member_id'`
- `Duplicate entry 'k619234203iobqmjuv' for key 'point_balances.uk_point_balances_member_id'`

즉, 주문 생성 실패(500)의 1차 원인은 `point_balances` 초기 insert 경합(중복키)으로 판단된다.

추가로 `2026-03-19T23:42:*` 구간에서 아래 WARN이 `37`회 확인되었다.

- `CoreException : 사용 취소할 수 없는 쿠폰입니다.`

해당 WARN은 취소 경로 경합/재시도 흐름에서 발생한 것으로 보이며, 취소 시나리오의 성공률 지표에 영향을 준다.

## Conclusion

- 성능 지표(응답시간)는 안정적이나, 기능 신뢰성은 주문 생성 초기 동시 구간에서 흔들린다.
- 쿠폰/포인트를 포함하면 실패가 `주문 생성` 단계에 집중되며, 현재는 `point_balances` 초기화 동시성 문제가 우선순위 1이다.
- 주문취소 자체(`200`, `409`)는 생성 성공 케이스에서는 기대대로 동작하지만, 생성 실패와 쿠폰 취소 경합 WARN이 전체 시나리오 통과율을 낮춘다.

## Raw Artifacts

- `docs/performance/k6/results/20260319-234203-order-create.txt`
- `docs/performance/k6/results/20260319-234203-order-create-summary.json`
- `docs/performance/k6/results/20260319-234203-order-create-cancel.txt`
- `docs/performance/k6/results/20260319-234203-order-create-cancel-summary.json`
- `docs/performance/k6/results/20260319-234203-order-cancel-idempotency.txt`
- `docs/performance/k6/results/20260319-234203-order-cancel-idempotency-summary.json`
