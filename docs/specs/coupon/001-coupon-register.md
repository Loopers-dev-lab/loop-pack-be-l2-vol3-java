# 쿠폰 등록

## 배경
관리자가 할인 쿠폰 템플릿을 등록하여 사용자에게 발급할 수 있어야 한다.

## API
POST /api-admin/v1/coupons

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| name | String | O | 1~100자 |
| type | String | O | FIXED / RATE |
| value | Integer | O | 1 이상; RATE: 최대 100 |
| minOrderAmount | BigDecimal | X | 0 이상 |
| maxIssueCount | Integer | O | 1 이상 |
| expiredAt | LocalDateTime | O | 현재 이후 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 쿠폰 ID |
| name | String | 쿠폰명 |
| type | String | FIXED / RATE |
| value | Integer | 할인값 |
| minOrderAmount | BigDecimal | 최소 주문 금액 (null 가능) |
| maxIssueCount | Integer | 총 발급 수량 |
| issuedCount | Integer | 현재 발급 수량 (0) |
| expiredAt | LocalDateTime | 만료일 |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 유효한 정보로 등록하면 200 응답과 생성된 쿠폰 정보를 반환한다
- [ ] issuedCount는 0으로 초기화된다
- [ ] RATE 타입의 value가 100을 초과하면 400 응답, 메시지: "유효하지 않은 할인값입니다"
- [ ] expiredAt이 현재보다 과거이면 400 응답, 메시지: "만료일은 현재 이후여야 합니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- type은 등록 후 변경 불가
