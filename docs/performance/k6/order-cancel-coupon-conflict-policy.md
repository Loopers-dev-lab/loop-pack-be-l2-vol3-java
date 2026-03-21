# Order Cancel Coupon Conflict Policy

## Scope

- 대상: 주문 취소 시점의 쿠폰 사용 취소(`CouponApplicationService.cancelUse`)
- 목적: 중복 취소/이미 취소된 상태에서의 기대 동작과 응답 코드를 고정한다.

## Current Rule

- 주문 취소에서 쿠폰 사용 취소는 원자적 상태 전이(`USED -> AVAILABLE`)가 성공할 때만 성공으로 본다.
- 이미 `AVAILABLE` 상태이거나 취소 가능한 상태가 아니면 충돌(`CONFLICT`)로 처리한다.

## Expected Responses

| Case | Condition | Expected HTTP | errorCode | message |
|---|---|---:|---|---|
| 1st cancel success | issued coupon status is `USED` | `200` | - | - |
| Duplicate cancel | issued coupon status is already `AVAILABLE` | `409` | `Conflict` | `사용 취소할 수 없는 쿠폰입니다.` |
| Not issued coupon | no issued coupon row for member/coupon | `404` | `Not Found` | `발급된 쿠폰을 찾을 수 없습니다.` |
| Owner mismatch | requester and coupon owner do not match | `400` | `Bad Request` | `요청자와 쿠폰 소유자가 일치하지 않습니다.` |

## Notes for k6 / Observability

- `order-cancel-idempotency` 시나리오에서 second cancel이 `409`인 것은 정상이다.
- 따라서 서버 로그의 `CoreException : 사용 취소할 수 없는 쿠폰입니다.`는 중복 취소를 검증하는 과정에서 발생할 수 있다.
- 운영 경보에서는 해당 로그를 실패로 오탐하지 않도록, 시나리오 태그(`X-K6-Scenario`)와 함께 분류한다.
