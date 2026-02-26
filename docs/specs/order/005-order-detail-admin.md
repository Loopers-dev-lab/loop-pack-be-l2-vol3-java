# 주문 상세 조회 (Admin)

## 배경
관리자가 특정 주문의 상세 정보를 확인하여 주문 현황을 파악할 수 있어야 한다.

## API
GET /api-admin/v1/orders/{orderId}

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 주문 ID |
| userId | Long | 주문자 ID |
| totalAmount | BigDecimal | 총 주문 금액 |
| orderItems[] | Array | 주문 상품 목록 |
| orderItems[].productId | Long | 상품 ID |
| orderItems[].productName | String | 상품명 (스냅샷) |
| orderItems[].price | BigDecimal | 상품 가격 (스냅샷) |
| orderItems[].quantity | Integer | 주문 수량 |
| orderItems[].orderPrice | BigDecimal | 주문 금액 (price × quantity) |
| createdAt | LocalDateTime | 주문일시 |

## 인수 조건
- [ ] 주문을 조회하면 200 응답과 주문 상세 정보를 반환한다
- [ ] 주문 상품은 스냅샷 정보(상품명, 가격)로 반환한다
- [ ] 해당 ID의 주문 데이터가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 모든 주문을 조회할 수 있다 (사용자 제한 없음)
