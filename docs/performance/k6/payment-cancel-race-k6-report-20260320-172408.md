# K6 Cross-Concurrency Report (Payment Start vs Order Cancel)

- Run ID: `20260320-172408`
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

- `http_req_duration p(95)<1800`: PASS (`279.62ms`)
- `race_status_contract rate>0.99`: PASS (`100.00%`)
- `race_no_server_error rate>0.99`: PASS (`100.00%`)
- `race_order_query_success rate>0.99`: PASS (`100.00%`)
- `race_payment_query_success rate>0.99`: PASS (`100.00%`)
- `http_req_failed rate<0.02`: PASS (`0.00%`)

## Core Metrics

- Iterations: `80`
- HTTP Requests: `523`
- HTTP avg latency: `150.64ms`
- HTTP p95 latency: `279.62ms`
- HTTP failed rate: `0.00%` (`0/523`)

Custom metrics:

- `race_status_contract`: `80/80` (100.00%)
- `race_no_server_error`: `80/80` (100.00%)
- `race_cancel_applied`: `80/80` (100.00%)
- `race_payment_created_count`: `71`
- `race_payment_conflict_count`: `6`
- `race_unresolved_after_reconcile_count`: not observed in this run (counter not emitted)

## Findings

1. 동일 시나리오 재실행에서 `http_req_failed=0.00%`로 목표(`1% 미만`)를 재확인했다.
2. race 계약/서버 에러 관련 threshold가 모두 PASS로 회복됐다.
3. `REQUESTED` 잔류를 직접 집계하던 `race_unresolved_after_reconcile_count`는 본 런에서 관측되지 않았다.

## Implemented Convergence Policy Changes

1. `PaymentUseCase.reconcile`:
   - 취소 주문 + `REQUESTED` 결제의 취소 재시도 분기 강화
   - PG 조회/취소 실패 시 `CANCEL_RECONCILE_REQUIRED`로 전이해 수습 가능 상태 유지
2. `Payment` 도메인:
   - `CANCEL_REQUESTED`/`CANCEL_RECONCILE_REQUIRED` 상태에서 거래 키 null 허용(수습 대기 상태 표현)
3. `PaymentRepositoryImpl`:
   - 저장 시 `saveAndFlush`로 DB 유니크 충돌을 트랜잭션 내부에서 즉시 감지
4. `ApiControllerAdvice`:
   - `DataIntegrityViolationException`을 `409(CONFLICT)`로 매핑

## Artifacts

- `docs/performance/k6/results/20260320-172408-payment-cancel-race.txt`
- `docs/performance/k6/results/20260320-172408-payment-cancel-race-summary.json`
