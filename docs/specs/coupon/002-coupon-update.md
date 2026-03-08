# 쿠폰 수정

## 배경
관리자가 등록된 쿠폰 템플릿의 정보를 수정할 수 있어야 한다.

## API
PATCH /api-admin/v1/coupons/{couponId}

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| couponId | Long | O | Path Variable |
| name | String | X | 1~100자 |
| value | Integer | X | 1 이상; RATE: 최대 100 |
| minOrderAmount | BigDecimal | X | 0 이상 |
| maxIssueCount | Integer | X | 1 이상, 현재 발급 수량 이상 |
| expiredAt | LocalDateTime | X | 현재 이후 |

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
- [ ] 유효한 정보로 수정하면 200 응답과 수정된 쿠폰 정보를 반환한다
- [ ] 전달된 필드만 수정하고 나머지는 유지한다
- [ ] type 필드를 전달하면 400 응답, 메시지: "쿠폰 유형은 변경할 수 없습니다"
- [ ] RATE 타입의 value가 100을 초과하면 400 응답, 메시지: "유효하지 않은 할인값입니다"
- [ ] maxIssueCount를 현재 발급 수량보다 작게 설정하면 400 응답, 메시지: "현재 발급 수량보다 작게 설정할 수 없습니다"
- [ ] expiredAt이 현재보다 과거이면 400 응답, 메시지: "만료일은 현재 이후여야 합니다"
- [ ] 쿠폰이 미존재하면 404 응답, 메시지: "존재하지 않는 쿠폰입니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- PATCH 방식: 전달된 필드만 수정하고 나머지는 유지한다
- type은 불변 필드로, 등록 후 변경할 수 없다
- 삭제된 쿠폰은 미존재로 처리한다
