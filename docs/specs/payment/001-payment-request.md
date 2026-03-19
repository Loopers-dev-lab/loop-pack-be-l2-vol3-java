# 결제 요청

## 배경
사용자가 주문에 대해 카드 결제를 요청할 수 있어야 한다. PG 시스템과 비동기로 연동하며, 장애 상황에서도 안정적으로 동작해야 한다.

## API
POST /api/v1/payments

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderId | Long | O | 존재하는 주문 ID |
| cardType | String | O | SAMSUNG, KB, HYUNDAI 중 하나 |
| cardNo | String | O | xxxx-xxxx-xxxx-xxxx 형식 (숫자와 하이픈) |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 결제 ID |
| orderId | Long | 주문 ID |
| transactionKey | String | PG 거래 키 (보류 시 null) |
| cardType | String | 카드 종류 |
| cardNo | String | 카드 번호 |
| amount | BigDecimal | 결제 금액 |
| status | String | 결제 상태 (PENDING, IN_PROGRESS, FAILED) |
| failReason | String | 실패 사유 (null 가능) |
| createdAt | LocalDateTime | 결제 요청일시 |

## 인수 조건
- [ ] 유효한 정보로 결제 요청하면 200 응답과 결제 정보를 반환한다
- [ ] PG 접수 성공 시 결제 상태는 IN_PROGRESS이고 transactionKey가 반환된다
- [ ] 결제 금액은 주문의 finalAmount를 사용한다
- [ ] PG 요청 타임아웃 시 200 응답, 결제 상태는 PENDING, transactionKey는 null이다
- [ ] PG 요청 실패(서버 에러) 시 500 응답, 결제 상태는 FAILED로 저장된다
- [ ] 해당 주문이 존재하지 않으면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 본인의 주문이 아니면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 주문 상태가 PENDING이 아니면 400 응답, 메시지: "결제할 수 없는 주문 상태입니다"
- [ ] 해당 주문에 성공 또는 보류 상태의 결제가 이미 존재하면 409 응답, 메시지: "이미 결제가 진행 중이거나 완료된 주문입니다"
- [ ] 서킷 브레이커가 열린 상태에서 요청하면 500 응답, 메시지: "현재 결제 서비스를 이용할 수 없습니다. 잠시 후 다시 시도해주세요"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 결제 생성(REQUESTED)은 트랜잭션 안에서, PG 호출은 트랜잭션 밖에서 처리한다
- PG 호출 시 서킷 브레이커와 재시도 정책을 적용한다
- PG callbackUrl은 시스템 설정값을 사용한다 (사용자 입력 아님)
