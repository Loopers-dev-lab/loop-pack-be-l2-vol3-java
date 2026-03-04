# 주문 요청

## 배경
사용자가 여러 상품을 한 번에 주문하고, 쿠폰을 적용해 할인받을 수 있어야 한다.

## API
POST /api/v1/orders

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| orderItems | Array | O | 주문 상품 목록, 1~100건 |
| orderItems[].productId | Long | O | 활성 상품 ID |
| orderItems[].quantity | Integer | O | 1 ~ 9,999,999 |
| couponId | Long | X | 발급 쿠폰 ID |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 주문 ID |
| totalAmount | BigDecimal | 쿠폰 적용 전 총 주문 금액 |
| discountAmount | BigDecimal | 할인 금액 (쿠폰 미적용 시 0) |
| finalAmount | BigDecimal | 최종 결제 금액 |
| couponId | Long | 적용된 발급 쿠폰 ID (null 가능) |
| orderItems[] | Array | 주문 상품 목록 |
| orderItems[].productId | Long | 상품 ID |
| orderItems[].productName | String | 상품명 (스냅샷) |
| orderItems[].price | BigDecimal | 상품 가격 (스냅샷) |
| orderItems[].quantity | Integer | 주문 수량 |
| orderItems[].orderPrice | BigDecimal | 주문 금액 (price x quantity) |
| createdAt | LocalDateTime | 주문일시 |

## 인수 조건
- [ ] 유효한 정보로 주문하면 200 응답과 생성된 주문 정보를 반환한다
- [ ] 주문 시 해당 상품의 재고가 주문 수량만큼 차감된다
- [ ] 주문 시점의 상품 정보(상품명, 가격)를 스냅샷으로 저장한다
- [ ] 원본 상품이 수정/삭제되어도 주문 스냅샷은 영향받지 않는다
- [ ] totalAmount는 각 주문 상품의 orderPrice 합계이다
- [ ] 쿠폰 미적용 시 discountAmount는 0, finalAmount는 totalAmount와 동일하다
- [ ] 정액(FIXED) 쿠폰 적용 시 discountAmount는 쿠폰의 value이며, totalAmount를 초과하면 totalAmount로 제한된다
- [ ] 정률(RATE) 쿠폰 적용 시 discountAmount는 totalAmount x value / 100이다
- [ ] finalAmount는 totalAmount - discountAmount이다 (최소 0)
- [ ] 쿠폰 적용 시 해당 발급 쿠폰이 USED 상태로 변경된다
- [ ] 동시에 같은 쿠폰으로 주문해도 쿠폰은 한 번만 사용된다
- [ ] 주문 상품 중 미존재 상품이 포함되면 404 응답, 메시지: "존재하지 않는 상품이 포함되어 있습니다"
- [ ] 주문 상품 중 삭제된 상품이 포함되면 404 응답, 메시지: "존재하지 않는 상품입니다"
- [ ] 주문 상품 중 하나라도 재고가 부족하면 400 응답, 메시지: "재고가 부족한 상품이 있습니다"
- [ ] 재고 부족 시 전체 주문이 실패하며, 어떤 상품의 재고도 차감되지 않는다
- [ ] 동일 productId가 중복으로 포함되면 400 응답, 메시지: "주문 상품이 중복되었습니다"
- [ ] 적용한 쿠폰이 미존재하거나 삭제되었으면 404 응답, 메시지: "존재하지 않는 쿠폰입니다"
- [ ] 타인 소유의 쿠폰이면 404 응답, 메시지: "존재하지 않는 쿠폰입니다"
- [ ] 이미 사용되었거나 만료된 쿠폰이면 400 응답, 메시지: "사용할 수 없는 쿠폰입니다"
- [ ] 최소 주문 금액 미달이면 400 응답, 메시지: "최소 주문 금액 조건을 충족하지 않습니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 주문 상품 중 하나라도 재고가 부족하면 전체 주문이 실패한다 (부분 성공 없음)
- 재고 차감, 쿠폰 사용 처리, 주문 생성은 같은 트랜잭션에서 처리한다
- 주문 상태 관리는 이번 스코프에 포함되지 않는다
- 주문 상세/목록 조회 명세서도 discountAmount, finalAmount, couponId 필드 추가가 필요하다
