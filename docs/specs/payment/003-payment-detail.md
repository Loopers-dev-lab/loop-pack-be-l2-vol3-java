# 결제 상세 조회

## 배경
사용자가 결제 진행 상태를 확인할 수 있어야 한다.

## API
GET /api/v1/payments/{paymentId}

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
| status | String | 결제 상태 (REQUESTED, SUCCEEDED, FAILED, CANCELED) |
| failReason | String | 실패 사유 (null 가능) |
| createdAt | LocalDateTime | 결제 요청일시 |

## 인수 조건
- [ ] 본인의 결제를 조회하면 200 응답과 결제 상세 정보를 반환한다
- [ ] 해당 결제가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 결제입니다"
- [ ] 본인의 결제가 아니면 404 응답, 메시지: "존재하지 않는 결제입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 본인의 결제만 조회 가능하다
- 타인의 결제 접근 시 존재 여부를 노출하지 않기 위해 미존재로 응답한다
