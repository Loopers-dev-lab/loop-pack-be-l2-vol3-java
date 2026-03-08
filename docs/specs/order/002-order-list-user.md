# 주문 목록 조회 (User)

## 배경
사용자가 본인의 주문 내역을 기간별로 조회하여 구매 이력을 확인할 수 있어야 한다.

## API
GET /api/v1/orders

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| startDate | LocalDate | X | 조회 시작일 (해당일 00:00:00부터) |
| endDate | LocalDate | X | 조회 종료일 (해당일 23:59:59까지) |
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 주문 목록 |
| content[].id | Long | 주문 ID |
| content[].totalAmount | BigDecimal | 총 주문 금액 |
| content[].discountAmount | BigDecimal | 할인 금액 (쿠폰 미적용 시 0) |
| content[].finalAmount | BigDecimal | 최종 결제 금액 |
| content[].issuedCouponId | Long | 적용된 발급 쿠폰 ID (null 가능) |
| content[].createdAt | LocalDateTime | 주문일시 |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조건 없이 조회하면 본인의 주문만 최신순으로 페이징하여 200 응답한다
- [ ] 타인의 주문은 반환하지 않는다
- [ ] startDate만 지정하면 해당일 이후 주문만 반환한다
- [ ] endDate만 지정하면 해당일 이전 주문만 반환한다
- [ ] startDate와 endDate를 모두 지정하면 해당 기간 내 주문만 반환한다
- [ ] startDate가 endDate보다 미래이면 400 응답, 메시지: "시작일은 종료일 이전이어야 합니다"
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 주문순 (createdAt DESC) 고정
- 본인의 주문만 조회 대상이다
