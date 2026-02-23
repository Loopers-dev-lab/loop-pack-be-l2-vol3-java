# 상품 상세 조회 (User)

## 배경
사용자가 특정 상품의 상세 정보를 확인할 수 있어야 한다.

## API
GET /api/v1/products/{productId}

## 인증
불필요

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
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 활성 상품을 조회하면 200 응답과 상품 상세 정보를 반환한다
- [ ] 삭제된 상품을 조회하면 404 응답, 메시지: "존재하지 않는 상품입니다"
- [ ] 해당 ID의 상품 데이터가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 상품입니다"

## 제약
- 활성 상품만 조회 가능하다
- 삭제된 상품은 데이터가 없는 것과 동일하게 미존재로 응답한다
