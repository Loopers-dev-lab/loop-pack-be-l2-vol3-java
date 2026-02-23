# 상품 상세 조회 (Admin)

## 배경
관리자가 특정 상품의 상세 정보를 확인할 수 있어야 한다.

## API
GET /api-admin/v1/products/{productId}

## 인증
LDAP (X-Loopers-Ldap 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| productId | Long | O | Path Variable |

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
| likeCount | Integer | 좋아요 수 |
| status | String | ACTIVE / DELETED |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |
| deletedAt | LocalDateTime | 삭제일시 (삭제된 경우) |

## 인수 조건
- [ ] 활성 상품을 조회하면 200 응답과 상품 상세 정보를 반환한다
- [ ] 삭제된 상품도 조회할 수 있으며, status가 DELETED로 표시된다
- [ ] 해당 ID의 상품 데이터가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 상품입니다"

## 제약
- Admin 상세 조회는 삭제 여부와 관계없이 데이터가 존재하면 반환한다
- 데이터 자체가 DB에 없는 경우만 404 처리한다
