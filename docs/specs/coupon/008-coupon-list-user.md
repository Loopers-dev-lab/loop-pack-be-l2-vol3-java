# 내 쿠폰 목록 조회

## 배경
사용자가 보유한 쿠폰의 목록과 상태를 확인할 수 있어야 한다.

## API
GET /api/v1/users/me/coupons

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 쿠폰 목록 |
| content[].id | Long | 발급 쿠폰 ID |
| content[].couponId | Long | 쿠폰 ID |
| content[].couponName | String | 쿠폰명 |
| content[].type | String | FIXED / RATE |
| content[].value | Integer | 할인값 |
| content[].minOrderAmount | BigDecimal | 최소 주문 금액 (null 가능) |
| content[].status | String | AVAILABLE / USED / EXPIRED |
| content[].expiredAt | LocalDateTime | 만료일 |
| content[].createdAt | LocalDateTime | 발급일시 |
| content[].usedAt | LocalDateTime | 사용일시 (null 가능) |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 사용자의 보유 쿠폰을 최신 발급순으로 페이징하여 200 응답한다
- [ ] 각 쿠폰의 상태(AVAILABLE/USED/EXPIRED)를 함께 반환한다
- [ ] EXPIRED는 만료일 기준으로 동적 판정한다
- [ ] 삭제된 발급 쿠폰은 목록에서 제외된다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 발급순 (createdAt DESC) 고정
