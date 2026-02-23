# 브랜드 목록 조회 (User)

## 배경
사용자가 활성 브랜드 목록을 조회하여 브랜드를 탐색할 수 있어야 한다.

## API
GET /api/v1/brands

## 인증
불필요

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| name | String | X | 브랜드명 검색 키워드 (부분 일치) |
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 브랜드 목록 |
| content[].id | Long | 브랜드 ID |
| content[].name | String | 브랜드명 |
| content[].description | String | 브랜드 설명 |
| content[].createdAt | LocalDateTime | 등록일시 |
| content[].updatedAt | LocalDateTime | 수정일시 |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조건 없이 조회하면 활성 브랜드만 이름 오름차순으로 페이징하여 200 응답한다
- [ ] 삭제된 브랜드는 반환하지 않는다
- [ ] name 키워드로 검색하면 브랜드명에 해당 키워드가 포함된 활성 브랜드만 반환한다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답

## 제약
- 정렬 기준: 이름 오름차순 (name ASC) 고정
- 활성 브랜드만 조회 대상이다
