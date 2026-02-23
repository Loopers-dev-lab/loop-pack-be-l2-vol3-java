# 주문 목록 조회 (Admin)

## 배경
관리자가 전체 주문 내역을 조회하여 주문 현황을 관리할 수 있어야 한다.

## API
GET /api-admin/v1/orders

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 주문 목록 |
| content[].id | Long | 주문 ID |
| content[].userId | Long | 주문자 ID |
| content[].totalAmount | BigDecimal | 총 주문 금액 |
| content[].createdAt | LocalDateTime | 주문일시 |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 전체 주문을 최신순으로 페이징하여 200 응답한다
- [ ] 모든 사용자의 주문이 포함된다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 주문순 (createdAt DESC) 고정
