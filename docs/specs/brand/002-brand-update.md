# 브랜드 정보 수정

## 배경
관리자가 등록된 브랜드의 정보를 수정하여 최신 상태로 유지할 수 있어야 한다.

## API
PATCH /api-admin/v1/brands/{brandId}

## 인증
LDAP (X-Loopers-Ldap 헤더)

## 요청
| 필드 | 타입 | 필수 | 규칙 |
|------|------|------|------|
| brandId | Long | O | Path Variable |
| name | String | X | 1~100자 |
| description | String | X | 최대 500자 |

## 응답
| 필드 | 타입 | 설명 |
|------|------|------|
| id | Long | 브랜드 ID |
| name | String | 브랜드명 |
| description | String | 브랜드 설명 |
| status | String | ACTIVE |
| createdAt | LocalDateTime | 등록일시 |
| updatedAt | LocalDateTime | 수정일시 |

## 인수 조건
- [ ] 유효한 정보로 수정하면 200 응답과 수정된 브랜드 정보를 반환한다
- [ ] name만 보내면 name만 수정된다
- [ ] description만 보내면 description만 수정된다
- [ ] name과 description을 함께 보내면 둘 다 수정된다
- [ ] 브랜드명 변경 시 다른 브랜드와 중복되면 409 응답, 메시지: "이미 등록된 브랜드입니다"
- [ ] 삭제된 브랜드와 동일한 이름으로도 변경할 수 없다 (409 응답)
- [ ] 자기 자신의 현재 이름과 동일한 이름으로 수정 요청하면 정상 처리한다
- [ ] 브랜드가 미존재하면 404 응답, 메시지: "존재하지 않는 브랜드입니다"
- [ ] 요청 필드 규칙 위반 시 400 응답

## 제약
- PATCH 방식: 전달된 필드만 수정하고 나머지는 유지한다
- 삭제된 브랜드도 미존재로 처리한다
