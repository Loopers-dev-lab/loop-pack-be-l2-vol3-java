# 결제 상태 수동 확인

## 배경
PG 콜백이 수신되지 않은 경우, 사용자가 수동으로 PG에 결제 상태를 확인하여 결제를 확정할 수 있어야 한다.

## API
POST /api/v1/payments/{paymentId}/verify

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| paymentId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 결제 ID |
| orderId | Long | 주문 ID |
| transactionKey | String | PG 거래 키 (null 가능) |
| cardType | String | 카드 종류 |
| cardNo | String | 카드 번호 |
| amount | BigDecimal | 결제 금액 |
| status | String | 확정된 결제 상태 |
| failReason | String | 실패 사유 (null 가능) |
| createdAt | LocalDateTime | 결제 요청일시 |

## 인수 조건
- [ ] REQUESTED 상태의 결제를 확인 요청하면 PG 조회 후 상태가 확정되고 200 응답을 반환한다
- [ ] PG 조회 결과 결제 완료(found && done)이면 결제 상태가 SUCCEEDED로 변경된다 (주문은 이미 PAID)
- [ ] PG 조회 결과 결제 미완료이면 결제 상태가 FAILED로 변경되고, 보상 트랜잭션이 실행된다: 재고 확정 복원 + 주문 CANCELED + 쿠폰 복원
- [ ] 해당 결제가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 결제입니다"
- [ ] 본인의 결제가 아니면 404 응답, 메시지: "존재하지 않는 결제입니다"
- [ ] 이미 확정(SUCCEEDED/FAILED) 상태이면 400 응답, 메시지: "이미 확정된 결제입니다"
- [ ] PG 조회 API 호출이 실패하면 500 응답, 메시지: "결제 상태를 확인할 수 없습니다. 잠시 후 다시 시도해주세요"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- PG 조회 API 호출 시 서킷 브레이커를 적용한다 (pg-query 인스턴스)
- REQUESTED 상태는 PG에 요청 자체가 도달하지 않은 것이므로 조회 없이 FAILED 처리한다
