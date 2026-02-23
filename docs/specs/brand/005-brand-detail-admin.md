# 브랜드 상세 조회 (Admin)

## 배경
관리자가 특정 브랜드의 상세 정보를 확인할 수 있어야 한다.

## API
GET /api-admin/v1/brands/{brandId}

## 인증
Admin

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| brandId | Long | O | Path Variable |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 브랜드 ID |
| name | String | 브랜드명 |
| description | String | 브랜드 설명 |
| status | String | ACTIVE / DELETED |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |
| deletedAt | LocalDateTime | 삭제일시 (삭제된 경우) |

## 인수 조건
- [ ] 활성 브랜드를 조회하면 200 응답과 브랜드 상세 정보를 반환한다
- [ ] 삭제된 브랜드도 조회할 수 있으며, status가 DELETED로 표시된다
- [ ] 해당 ID의 브랜드 데이터가 존재하지 않으면 404 응답, 메시지: "존재하지 않는 브랜드입니다"
- [ ] 인증 헤더가 누락되면 401 응답, 메시지: "인증 헤더가 필요합니다"
- [ ] 인증에 실패하면 401 응답, 메시지: "인증에 실패했습니다"

## 제약
- Admin 상세 조회는 삭제 여부와 관계없이 데이터가 존재하면 반환한다
- 데이터 자체가 DB에 없는 경우만 404 처리한다
