# 상품 등록

## 배경
관리자가 특정 브랜드에 속한 신규 상품을 등록할 수 있어야 한다.

## API
POST /api-admin/v1/products

## 인증
LDAP (X-Loopers-Ldap 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| brandId | Long | O | 활성 브랜드 ID |
| name | String | O | 1~200자 |
| price | Integer | O | 0 ~ 999,999,999 |
| stockQuantity | Integer | O | 0 ~ 9,999,999 |
| description | String | X | 최대 1,000자 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 상품 ID |
| brandId | Long | 소속 브랜드 ID |
| brandName | String | 소속 브랜드명 |
| name | String | 상품명 |
| price | Integer | 가격 |
| stockQuantity | Integer | 재고 수량 |
| description | String | 상품 설명 |
| likeCount | Integer | 좋아요 수 (초기값 0) |
| status | String | ACTIVE |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 유효한 정보로 등록하면 200 응답과 생성된 상품 정보를 반환한다
- [ ] 상품은 활성 상태로 생성된다
- [ ] likeCount는 0으로 초기화된다
- [ ] 소속 브랜드가 미존재하면 404 응답, 메시지: "존재하지 않는 브랜드입니다"
- [ ] 요청 필드 규칙 위반 시 400 응답

## 제약
- 소속 브랜드는 활성 상태여야 한다
