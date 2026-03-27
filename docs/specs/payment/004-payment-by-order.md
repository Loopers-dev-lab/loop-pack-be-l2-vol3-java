# 주문별 결제 조회

## 배경
사용자가 주문에 연결된 결제 정보를 확인할 수 있어야 한다.

## API
GET /api/v1/orders/{orderId}/payment

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 결제 ID (결제 없으면 null) |
| orderId | Long | 주문 ID |
| transactionKey | String | PG 거래 키 (null 가능) |
| cardType | String | 카드 종류 (null 가능) |
| cardNo | String | 카드 번호 (null 가능) |
| amount | BigDecimal | 결제 금액 (null 가능) |
| status | String | 결제 상태 (null 가능) |
| failReason | String | 실패 사유 (null 가능) |
| createdAt | LocalDateTime | 결제 요청일시 (null 가능) |

## 인수 조건
- [ ] 결제가 있는 주문을 조회하면 200 응답과 결제 정보를 반환한다
- [ ] 결제가 없는 주문을 조회하면 200 응답과 빈 결제 정보(null 필드)를 반환한다
- [ ] 해당 주문이 존재하지 않으면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 본인의 주문이 아니면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 1주문 1결제이므로 단일 결제 정보를 반환한다 (목록이 아님)
- 실패한 결제가 있고 재결제 전이면, 가장 최근 결제 정보를 반환한다
