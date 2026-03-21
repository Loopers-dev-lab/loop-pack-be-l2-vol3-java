# ADR: 외부 API 연동 구간 k6 검증 기준 정리 (주문/결제/취소)

- 상태: Accepted
- 날짜: 2026-03-20
- 작성자: OpenCode
- 관련 모듈: `apps/commerce-api`
- 관련 문서:
  - `docs/performance/k6/README.md`
  - `docs/performance/k6/order-ordercancel-k6-report-20260319-225359.md`
  - `docs/performance/k6/order-ordercancel-k6-report-20260319-232057.md`
  - `docs/performance/k6/order-ordercancel-k6-report-20260319-234203.md`
  - `docs/performance/k6/order-ordercancel-k6-report-20260320-000637.md`
  - `docs/performance/k6/payment-cancel-race-k6-report-20260320-102457.md`
  - `docs/performance/k6/payment-cancel-race-k6-report-20260320-165632.md`
  - `docs/performance/k6/payment-cancel-race-k6-report-20260320-172408.md`
  - `docs/performance/k6/payment-cancel-race-k6-report-20260320-175026.md`
  - `docs/performance/k6/payment-cancel-race-k6-report-20260320-175630-batch5.md`
  - `docs/performance/k6/payment-pg-resilience-tuning-20260320.md`

## 맥락

외부 API(PG) 연동이 포함된 주문/결제/취소 경로에서, 기능 정확성과 운영 안정성 판단 기준이 여러 리포트로 분산되어 있었다.
특히 다음 2개 축을 한 번에 관리할 필요가 있었다.

1. 주문/취소 API의 부하 및 멱등성 시나리오에서 5xx/실패율이 없는지
2. 결제 시작 vs 주문 취소 교차 동시성(race)에서 상태 계약과 후속 수렴(reconcile)이 유지되는지

## 실험 범위

### 포함

- 주문/취소 외부 API 시나리오
  - `order-create`
  - `order-create-cancel`
  - `order-cancel-idempotency`
- 교차 동시성 시나리오
  - `payment-cancel-race` (`POST /api/v1/payments` vs `PATCH /api/v1/orders/{orderId}/cancel`)
- PG resilience 정책 조정 후 재검증
  - circuit breaker + retry + reconcile 흐름

### 제외

- 포인트/재고의 최종 수량 정합성 정량 검증(전용 조회 API 부재로 k6 범위 밖)

## 실행 이력 요약

| Run ID | 문서 | 핵심 결과 |
|---|---|---|
| `20260319-225359` | `order-ordercancel-k6-report-20260319-225359.md` | 주문/취소/멱등 시나리오 전체 threshold PASS |
| `20260319-232057` | `order-ordercancel-k6-report-20260319-232057.md` | 쿠폰+포인트 포함 시 `order_create_success`, `http_req_failed`, cancel 멱등 지표 일부 FAIL |
| `20260319-234203` | `order-ordercancel-k6-report-20260319-234203.md` | 계측 강화로 원인 특정: `point_balances.uk_point_balances_member_id` 중복키로 create_order 500 집중 |
| `20260320-000637` | `order-ordercancel-k6-report-20260320-000637.md` | 포인트 초기화 upsert 적용 후 3개 시나리오 threshold 전부 PASS, `order_create_failure_5xx=0` |
| `20260320-102457` | `payment-cancel-race-k6-report-20260320-102457.md` | race 상태 계약은 PASS, 그러나 `http_req_failed=14.03%`, `race_failure_5xx=79`, `race_unresolved_after_reconcile_count=80` |
| `20260320-165632` | `payment-cancel-race-k6-report-20260320-165632.md` | race 재실행에서 `http_req_failed=0.00%`, 상태/조회 threshold PASS, 다만 `race_unresolved_after_reconcile_count=80` 지속 |
| `20260320-172408` | `payment-cancel-race-k6-report-20260320-172408.md` | `http_req_failed=0.00%`, 상태/조회/서버에러 threshold PASS, `race_unresolved_after_reconcile_count` 미관측 |
| `20260320-175026` | `payment-cancel-race-k6-report-20260320-175026.md` | deadlock 경합 응답을 `409`로 수렴시킨 뒤 동일 시나리오 threshold PASS |
| `20260320-175630-batch5` | `payment-cancel-race-k6-report-20260320-175630-batch5.md` | 5회 반복 실행 모두 PASS(플레이크 미관측), `http_req_failed=0.00%`, `race_unresolved_after_reconcile_count=0` |
| `20260320-003622` | `payment-pg-resilience-tuning-20260320.md` | `order-create/order-create-cancel/order-cancel-idempotency` threshold PASS, PG request/cancel retry 1회 정책 검증 |

## 최종 결정

외부 API 연동 k6의 운영 판단 기준을 다음으로 확정한다.

1. **성공 기준(필수)**
   - 주문/취소 시나리오: 기존 threshold 전부 PASS
   - 교차 동시성 시나리오: `http_req_failed < 2%`, 상태 계약/조회 성공률 99% 이상
   - 서버 에러 지표: `race_failure_5xx=0` 유지

2. **현재 채택 상태(2026-03-20 기준)**
   - 주문/취소/멱등 시나리오는 PASS 상태로 채택
   - payment-cancel-race는 단일 런이 아닌 반복 런(5회) 기준으로도 PASS 상태를 확인해 채택
   - `race_unresolved_after_reconcile_count`는 최신 반복 런에서 미관측(0)이며, 회귀 감시 지표로 계속 추적

3. **해석 원칙**
   - race 시나리오에서 상태 계약 PASS와 수렴 품질 PASS는 분리 평가한다.
   - 즉시 장애(5xx, high fail-rate)와 지연 수렴(`REQUESTED` 잔류)은 서로 다른 수준의 위험으로 관리한다.

## 근거 요약

- 포인트 초기화 경합은 DB-native upsert(`INSERT ... ON DUPLICATE KEY UPDATE`)로 완화되었고, 이후 주문 생성 500이 재현되지 않았다.
- PG resilience 정책(`pg-request-connection.maxAttempts=1`, `pg-cancel-connection.maxAttempts=1`)은 외부 비멱등 호출 중복 위험을 줄이는 방향으로 검증됐다.
- race 재실행(`20260320-172408`, `20260320-175026`)에서 실패율/5xx가 회복되었고, 5회 반복 런(`20260320-175630-batch5`)에서도 동일 결과가 재현되었다.

## 트레이드오프

- 장점
  - 외부 API 장애 징후(5xx/failed-rate)를 빠르게 감지하고 회귀 판단이 가능하다.
  - 주문/취소/결제 경합을 동일 도구(k6)로 반복 검증할 수 있다.

- 한계
  - 현재 시나리오만으로 재고/포인트의 최종 잔액 정합성을 직접 증명하지 못한다.
  - 수렴 지표는 운영/스케줄러 정책에 따라 변동 가능성이 있어, 단기 PASS만으로 완전성 보장을 하긴 어렵다.

## 후속 작업

1. `payment-cancel-race` 후속 수렴 정책 보강 후 `race_unresolved_after_reconcile_count`를 감소시키는 목표치를 별도 설정
2. 재고/포인트 정합성 검증용 조회 API 또는 진단 엔드포인트를 마련해 k6 검증 범위를 확장
3. CI/로컬 실행 시 Docker socket 환경(예: Colima) 차이를 흡수하는 실행 가이드 표준화
