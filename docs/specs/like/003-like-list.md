# 좋아요 상품 목록 조회

## 배경
사용자가 좋아요한 상품을 모아 볼 수 있어야 한다.

## API
GET /api/v1/likes

## 인증
User

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 좋아요 상품 목록 |
| content[].id | Long | 상품 ID |
| content[].brandId | Long | 소속 브랜드 ID |
| content[].brandName | String | 소속 브랜드명 |
| content[].name | String | 상품명 |
| content[].price | BigDecimal | 가격 |
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
- [ ] 좋아요한 상품 목록을 좋아요 등록순(최신순)으로 페이징하여 200 응답한다
- [ ] 활성 상품만 반환한다 (삭제된 상품은 제외)
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 좋아요 등록순 (Like.createdAt DESC) 고정
- 활성 상품만 조회 대상이다
