# 쿠폰 목록 조회 (Admin)

## 배경
관리자가 등록된 쿠폰 템플릿 목록을 페이징하여 조회할 수 있어야 한다.

## API
GET /api-admin/v1/coupons

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
| content[] | Array | 쿠폰 목록 |
| content[].id | Long | 쿠폰 ID |
| content[].name | String | 쿠폰명 |
| content[].type | String | FIXED / RATE |
| content[].value | Integer | 할인값 |
| content[].minOrderAmount | BigDecimal | 최소 주문 금액 (null 가능) |
| content[].maxIssueCount | Integer | 총 발급 수량 |
| content[].issuedCount | Integer | 현재 발급 수량 |
| content[].expiredAt | LocalDateTime | 만료일 |
| content[].createdAt | LocalDateTime | 등록일시 |
| content[].updatedAt | LocalDateTime | 수정일시 |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조회하면 활성 쿠폰 템플릿을 최신 등록순으로 페이징하여 200 응답한다
- [ ] 삭제된 쿠폰은 목록에서 제외된다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 등록순 (createdAt DESC) 고정
