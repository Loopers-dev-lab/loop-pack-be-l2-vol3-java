# 결제 콜백 수신

## 배경
PG 시스템이 결제 처리를 완료한 후 결과를 콜백으로 전달한다. 콜백 수신 시 결제 상태를 확정하고, 실패이면 보상 트랜잭션을 수행한다.

## API
POST /api/v1/payments/callback

## 인증
불필요

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| transactionKey | String | O | PG 거래 키 |
| orderId | String | O | 주문 ID (PG 기준) |
| cardType | String | O | 카드 종류 |
| cardNo | String | O | 카드 번호 |
| amount | Long | O | 결제 금액 |
| status | String | O | SUCCESS 또는 FAILED |
| reason | String | X | 처리 사유 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| - | - | 본문 없이 200 응답 |

## 인수 조건
- [ ] PG 콜백으로 SUCCESS를 수신하면 결제 상태가 SUCCEEDED로 변경된다 (주문은 이미 PAID)
- [ ] PG 콜백으로 FAILED를 수신하면 결제 상태가 FAILED로 변경되고 failReason이 저장된다
- [ ] PG 콜백으로 FAILED를 수신하면 보상 트랜잭션이 실행된다: 재고 확정 복원 + 주문 CANCELED + 쿠폰 복원
- [ ] 이미 확정(SUCCEEDED/FAILED)된 결제에 대한 콜백이면 무시하고 200 응답한다 (멱등성)
- [ ] transactionKey에 해당하는 결제가 없으면 200 응답한다 (무시)

## 제약
- 인증이 불필요하다 (PG 시스템이 호출하는 내부 API)
- 콜백 처리와 보상 트랜잭션은 같은 트랜잭션에서 처리한다
