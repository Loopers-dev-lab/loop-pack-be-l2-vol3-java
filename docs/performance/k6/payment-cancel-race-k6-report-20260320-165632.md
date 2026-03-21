# K6 Cross-Concurrency Report (Payment Start vs Order Cancel)

- Run ID: `20260320-165632`
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

- `http_req_duration p(95)<1800`: PASS (`245.3ms`)
- `race_status_contract rate>0.99`: PASS (`100.00%`)
- `race_no_server_error rate>0.99`: PASS (`100.00%`)
- `race_order_query_success rate>0.99`: PASS (`100.00%`)
- `race_payment_query_success rate>0.99`: PASS (`100.00%`)
- `http_req_failed rate<0.02`: PASS (`0.00%`)

## Core Metrics

- Iterations: `80`
- HTTP Requests: `563`
- HTTP avg latency: `131.62ms`
- HTTP p95 latency: `245.3ms`
- HTTP failed rate: `0.00%` (`0/563`)

Custom metrics:

- `race_status_contract`: `80/80` (100.00%)
- `race_no_server_error`: `80/80` (100.00%)
- `race_cancel_applied`: `80/80` (100.00%)
- `race_payment_created_count`: `77`
- `race_payment_conflict_count`: `3`
- `race_unresolved_after_reconcile_count`: `80`

## Findings

1. 교차 요청 진입 구간의 상태 계약은 유지됐고, 서버 에러 없이 동작했다.
2. 동일 시나리오 재실행에서 `http_req_failed=0.00%`로 목표(`1% 미만`)를 달성했다.
3. 다만 `race_unresolved_after_reconcile_count=80`으로, 취소 주문의 결제가 `REQUESTED`로 잔류하는 수렴 이슈는 남아 있다.

## Interpretation Scope

- 본 시나리오는 payment start와 order cancel의 교차 구간 및 후속 reconcile 호출까지 포함해 측정했다.
- 포인트/재고의 최종 수량 정합성은 전용 조회 API 부재로 본 리포트 범위에 포함하지 않았다.

## Follow-up Actions

1. cancel 주문 + `REQUESTED` 결제의 수렴 정책(재조회/재시도/전이 규칙) 보강
2. 수렴 보강 후 동일 시나리오 재실행으로 `race_unresolved_after_reconcile_count` 개선 확인
3. scheduler/manual reconcile 분담 기준을 문서화해 운영 가이드에 반영

## Artifacts

- `docs/performance/k6/results/20260320-165632-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-165632-payment-cancel-race-summary.json`
