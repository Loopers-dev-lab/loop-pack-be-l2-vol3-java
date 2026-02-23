# 상품 목록 조회 (User)

## 배경
사용자가 활성 상품을 탐색하여 구매 결정을 내릴 수 있어야 한다.

## API
GET /api/v1/products

## 인증
불필요

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| brandId | Long | X | 브랜드 ID 필터 |
| sort | String | X | RECENT / PRICE_ASC / LIKES_DESC, 기본값 RECENT |
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 상품 목록 |
| content[].id | Long | 상품 ID |
| content[].brandId | Long | 소속 브랜드 ID |
| content[].brandName | String | 소속 브랜드명 |
| content[].name | String | 상품명 |
| content[].price | Integer | 가격 |
| content[].stockQuantity | Integer | 재고 수량 |
| content[].description | String | 상품 설명 |
| content[].likeCount | Integer | 좋아요 수 |
| content[].createdAt | LocalDateTime | 등록일시 |
| content[].updatedAt | LocalDateTime | 수정일시 |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조건 없이 조회하면 활성 상품만 최신순으로 페이징하여 200 응답한다
- [ ] 삭제된 상품은 반환하지 않는다
- [ ] brandId로 필터링하면 해당 브랜드에 속한 활성 상품만 반환한다
- [ ] sort=RECENT이면 최신 등록순으로 정렬한다
- [ ] sort=PRICE_ASC이면 가격 오름차순으로 정렬한다
- [ ] sort=LIKES_DESC이면 좋아요 내림차순으로 정렬한다
- [ ] sort를 지정하지 않으면 기본값 RECENT로 정렬한다
- [ ] sort에 RECENT, PRICE_ASC, LIKES_DESC 이외의 값을 보내면 400 응답
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답

## 제약
- 활성 상품만 조회 대상이다
- 좋아요 내림차순 정렬은 좋아요(Like) 기능 구현이 선행되어야 한다
