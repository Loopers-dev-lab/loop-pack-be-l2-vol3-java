# 주문 요청

## 배경
사용자가 여러 상품을 한 번에 주문하여 구매할 수 있어야 한다.

## API
POST /api/v1/orders

## 인증
User (X-Loopers-LoginId + LoginPw 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderItems | Array | O | 주문 상품 목록, 1~100건 |
| orderItems[].productId | Long | O | 활성 상품 ID |
| orderItems[].quantity | Integer | O | 1 ~ 9,999,999 |

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
- [ ] 유효한 정보로 주문하면 200 응답과 생성된 주문 정보를 반환한다
- [ ] 주문 시 해당 상품의 재고가 주문 수량만큼 차감된다
- [ ] 주문 시점의 상품 정보(상품명, 가격, 브랜드명)를 스냅샷으로 저장한다
- [ ] 원본 상품이 수정/삭제되어도 주문 스냅샷은 영향받지 않는다
- [ ] totalAmount는 각 주문 상품의 orderPrice 합계이다
- [ ] 주문 상품 중 미존재 상품이 포함되면 404 응답, 메시지: "존재하지 않는 상품이 포함되어 있습니다"
- [ ] 주문 상품 중 하나라도 재고가 부족하면 400 응답, 메시지: "재고가 부족한 상품이 있습니다"
- [ ] 재고 부족 시 전체 주문이 실패하며, 어떤 상품의 재고도 차감되지 않는다
- [ ] 동일 productId가 중복으로 포함되면 400 응답, 메시지: "주문 상품이 중복되었습니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 주문 상품 중 하나라도 재고가 부족하면 전체 주문이 실패한다 (부분 성공 없음)
- 재고 차감과 주문 생성은 같은 트랜잭션에서 처리한다
- 주문 상태 관리는 이번 스코프에 포함되지 않는다
