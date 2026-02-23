# 상품 정보 수정

## 배경
관리자가 등록된 상품의 정보를 수정하여 최신 상태로 유지할 수 있어야 한다.

## API
PATCH /api-admin/v1/products/{productId}

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| productId | Long | O | Path Variable |
| name | String | X | 1~200자 |
| price | BigDecimal | X | 0 ~ 999,999,999 |
| stockQuantity | Integer | X | 0 ~ 9,999,999 |
| description | String | X | 최대 1,000자 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 상품 ID |
| brandId | Long | 소속 브랜드 ID |
| brandName | String | 소속 브랜드명 |
| name | String | 상품명 |
| price | BigDecimal | 가격 |
| stockQuantity | Integer | 재고 수량 |
| description | String | 상품 설명 |
| likeCount | Integer | 좋아요 수 |
| status | String | ACTIVE |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 유효한 정보로 수정하면 200 응답과 수정된 상품 정보를 반환한다
- [ ] 전달된 필드만 수정되고 나머지는 유지된다
- [ ] 소속 브랜드(brandId)는 변경할 수 없다
- [ ] 상품이 미존재하면 404 응답, 메시지: "존재하지 않는 상품입니다"
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- PATCH 방식: 전달된 필드만 수정하고 나머지는 유지한다
- brandId는 요청에 포함되어도 무시한다
- 삭제된 상품도 미존재로 처리한다
