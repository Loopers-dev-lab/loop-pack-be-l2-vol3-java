# 상품 목록 조회 (Admin)

## 배경
관리자가 전체 상품을 페이징하여 조회하고, 검색/필터링할 수 있어야 한다.

## API
GET /api-admin/v1/products

## 인증
LDAP (X-Loopers-Ldap 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| name | String | X | 상품명 검색 키워드 (부분 일치) |
| brandId | Long | X | 브랜드 ID 필터 |
| status | String | X | ACTIVE / DELETED |
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
| content[].status | String | ACTIVE / DELETED |
| content[].createdAt | LocalDateTime | 등록일시 |
| content[].updatedAt | LocalDateTime | 수정일시 |
| content[].deletedAt | LocalDateTime | 삭제일시 (삭제된 경우) |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조건 없이 조회하면 전체 상품을 최신 등록순으로 페이징하여 200 응답한다
- [ ] 삭제된 상품도 포함하여 반환한다
- [ ] name 키워드로 검색하면 상품명에 해당 키워드가 포함된 상품만 반환한다
- [ ] brandId로 필터링하면 해당 브랜드에 속한 상품만 반환한다
- [ ] status=ACTIVE로 필터링하면 활성 상품만 반환한다
- [ ] status=DELETED로 필터링하면 삭제된 상품만 반환한다
- [ ] status에 ACTIVE, DELETED 이외의 값을 보내면 400 응답
- [ ] name 검색, brandId 필터, status 필터를 동시에 적용할 수 있다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답

## 제약
- 정렬 기준: 최신 등록순 (createdAt DESC) 고정
