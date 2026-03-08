# 쿠폰 발급 내역 조회 (Admin)

## 배경
관리자가 특정 쿠폰의 발급 현황을 확인하여 운영에 활용할 수 있어야 한다.

## API
GET /api-admin/v1/coupons/{couponId}/issues

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| couponId | Long | O | Path Variable |
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 발급 내역 목록 |
| content[].id | Long | 발급 쿠폰 ID |
| content[].userId | Long | 사용자 ID |
| content[].loginId | String | 사용자 로그인 ID |
| content[].status | String | AVAILABLE / USED / EXPIRED |
| content[].createdAt | LocalDateTime | 발급일시 |
| content[].usedAt | LocalDateTime | 사용일시 (null 가능) |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 쿠폰의 발급 내역을 최신 발급순으로 페이징하여 200 응답한다
- [ ] 각 발급 쿠폰의 상태(AVAILABLE/USED/EXPIRED)를 함께 반환한다
- [ ] EXPIRED는 만료일 기준으로 동적 판정한다 (AVAILABLE이면서 만료일이 현재 이전이면 EXPIRED)
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 쿠폰이 미존재하면 404 응답, 메시지: "존재하지 않는 쿠폰입니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 발급순 (createdAt DESC) 고정
- 삭제된 쿠폰은 미존재로 처리한다
