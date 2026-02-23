# 주문 상세 조회 (User)

## 배경
사용자가 특정 주문의 상세 정보와 주문 상품 내역을 확인할 수 있어야 한다.

## API
GET /api/v1/orders/{orderId}

## 인증
User (X-Loopers-LoginId + LoginPw 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 주문 ID |
| totalAmount | Long | 총 주문 금액 |
| orderItems[] | Array | 주문 상품 목록 |
| orderItems[].productId | Long | 상품 ID |
| orderItems[].productName | String | 상품명 (스냅샷) |
| orderItems[].brandName | String | 브랜드명 (스냅샷) |
| orderItems[].price | Integer | 상품 가격 (스냅샷) |
| orderItems[].quantity | Integer | 주문 수량 |
| orderItems[].orderPrice | Long | 주문 금액 (price × quantity) |
| createdAt | LocalDateTime | 주문일시 |

## 인수 조건
- [ ] 본인의 주문을 조회하면 200 응답과 주문 상세 정보를 반환한다
- [ ] 주문 상품은 스냅샷 정보(상품명, 가격, 브랜드명)로 반환한다
- [ ] 해당 ID의 주문 데이터가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 본인의 주문이 아니면 404 응답, 메시지: "존재하지 않는 주문입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 본인의 주문만 조회 가능하다
- 타인의 주문 접근 시 존재 여부를 노출하지 않기 위해 미존재로 응답한다
