# 쿠폰 상세 조회 (Admin)

## 배경
관리자가 특정 쿠폰 템플릿의 상세 정보를 확인할 수 있어야 한다.

## API
GET /api-admin/v1/coupons/{couponId}

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| couponId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 쿠폰 ID |
| name | String | 쿠폰명 |
| type | String | FIXED / RATE |
| value | Integer | 할인값 |
| minOrderAmount | BigDecimal | 최소 주문 금액 (null 가능) |
| maxIssueCount | Integer | 총 발급 수량 |
| issuedCount | Integer | 현재 발급 수량 |
| expiredAt | LocalDateTime | 만료일 |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 활성 쿠폰을 조회하면 200 응답과 쿠폰 상세 정보를 반환한다
- [ ] 쿠폰이 미존재하면 404 응답, 메시지: "존재하지 않는 쿠폰입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 삭제된 쿠폰은 미존재로 처리한다
