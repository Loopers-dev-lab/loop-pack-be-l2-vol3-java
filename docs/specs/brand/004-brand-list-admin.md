# 브랜드 목록 조회 (Admin)

## 배경
관리자가 전체 브랜드를 페이징하여 조회하고, 검색/필터링할 수 있어야 한다.

## API
GET /api-admin/v1/brands

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| name | String | X | 브랜드명 검색 키워드 (부분 일치) |
| status | String | X | ACTIVE / DELETED |
| page | Integer | X | 0부터 시작, 기본값 0 |
| size | Integer | X | 1~100, 기본값 20 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| content[] | Array | 브랜드 목록 |
| content[].id | Long | 브랜드 ID |
| content[].name | String | 브랜드명 |
| content[].description | String | 브랜드 설명 |
| content[].status | String | ACTIVE / DELETED |
| content[].createdAt | LocalDateTime | 등록일시 |
| content[].updatedAt | LocalDateTime | 수정일시 |
| content[].deletedAt | LocalDateTime | 삭제일시 (삭제된 경우) |
| page | Integer | 현재 페이지 번호 |
| size | Integer | 페이지 크기 |
| totalElements | Long | 전체 데이터 수 |
| totalPages | Integer | 전체 페이지 수 |

## 인수 조건
- [ ] 조건 없이 조회하면 전체 브랜드를 최신 등록순으로 페이징하여 200 응답한다
- [ ] 삭제된 브랜드도 포함하여 반환한다
- [ ] name 키워드로 검색하면 브랜드명에 해당 키워드가 포함된 브랜드만 반환한다
- [ ] status=ACTIVE로 필터링하면 활성 브랜드만 반환한다
- [ ] status=DELETED로 필터링하면 삭제된 브랜드만 반환한다
- [ ] status에 ACTIVE, DELETED 이외의 값을 보내면 400 응답
- [ ] name 검색과 status 필터를 동시에 적용할 수 있다
- [ ] 결과가 없으면 빈 목록을 반환한다
- [ ] 요청 필드 규칙 위반 시 400 응답
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- 정렬 기준: 최신 등록순 (createdAt DESC) 고정
