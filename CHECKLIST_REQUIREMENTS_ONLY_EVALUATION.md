# CHECKLIST_REQUIREMENTS_ONLY 기준 평가 결과

## 평가 기준
- `CHECKLIST_REQUIREMENTS_ONLY.md` (총 66개, 항목당 1.515151점)
- 판정 방식: 각 항목 `1/0`

## 6.1 공통 (C1-C6)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| C1 | 1 | 사용자 정보 API에서 두 헤더를 모두 검증 |
| C2 | 0 | `loginId/name/email`에 `trim()` 전처리 로직 없음 |
| C3 | 0 | `password` 공백/개행/제어문자 금지 검사 누락 |
| C4 | 1 | 비즈니스 검증 경로에서 `CoreException + ErrorType` 사용 |
| C5 | 1 | `CoreException -> GlobalExceptionHandler -> ErrorResponse(code,message)` 충족 |
| C6 | 1 | `@Valid` 실패를 400으로 반환, 응답에 `code/message` 포함 |

## 6.2 회원가입 (S1-S34)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| S1 | 1 | `POST /api/v1/users` 존재 |
| S2 | 1 | 5개 필드 모두 필수 검증 |
| S3 | 1 | 성공 시 201 반환 |
| S4 | 1 | 응답 필드 `id/loginId/name/birthday/email` 반환 |
| S5 | 1 | 응답에 password/hash 미포함 |
| S6 | 1 | `loginId` 영문/숫자 패턴 적용 |
| S7 | 0 | `trim()+lowercase` 정규화 누락 |
| S8 | 0 | 길이 하한 4 검증 없음(최대 20만 검증) |
| S9 | 0 | 중복 검사가 정규화 기준(trim+lowercase) 아님 |
| S10 | 1 | 형식 위반 시 `INVALID_LOGIN_ID_FORMAT` |
| S11 | 1 | 중복 시 `USER_ALREADY_EXISTS(409)` |
| S12 | 1 | 비밀번호 길이 8~16 검증 |
| S13 | 1 | 대/소문자/숫자/특수문자 포함 검증 |
| S14 | 0 | 허용 문자 “only” 제한(배타 검증) 없음 |
| S15 | 0 | 공백/개행/제어문자 금지 규칙 누락 |
| S16 | 1 | password에 `trim()` 적용 없음 |
| S17 | 0 | `YYYY-MM-DD` 패턴 검사 누락 |
| S18 | 1 | `SHA-256 + Base64` 해시 저장 |
| S19 | 1 | 형식 위반 시 `INVALID_PASSWORD_FORMAT` |
| S20 | 0 | 생년월일 포함 케이스 전체(`YYYYMMDD`,`YYYY-MM-DD`)를 모두 차단하지 못함 |
| S21 | 0 | `name` 허용 문자에 공백 미포함 |
| S22 | 0 | `name` `trim()` 후 검증 로직 없음 |
| S23 | 0 | 길이 상한이 50이 아니라 100 |
| S24 | 1 | 이름 형식 위반 시 `INVALID_NAME_FORMAT` |
| S25 | 1 | `LocalDate`로 `yyyy-MM-dd` 파싱 경로 |
| S26 | 0 | “오늘 이전/1900-01-01 이후” 경계 조건과 불일치(오늘/1900-01-01 허용) |
| S27 | 0 | S26 경계 조건 불일치로 위반 케이스 전체를 `INVALID_BIRTHDAY`로 처리하지 못함 |
| S28 | 1 | 이메일 최대 길이 254 검증 |
| S29 | 1 | 이메일 기본 정규식 검증 |
| S30 | 1 | 정규식 허용 문자 집합으로 공백/제어문자 차단 |
| S31 | 1 | 도메인 `..`, 시작/끝 점 금지 검증 |
| S32 | 0 | 이메일 `trim()` 후 검증 로직 없음 |
| S33 | 1 | 이메일 위반 시 `INVALID_EMAIL_FORMAT` |
| S34 | 1 | 필수 필드 누락 시 400 반환 |

## 6.3 내 정보 조회 (M1-M7)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| M1 | 1 | `GET /api/v1/users/me` 존재 |
| M2 | 1 | 두 헤더 필수 검증 |
| M3 | 1 | 응답 필드 `loginId/name/birthday/email` |
| M4 | 1 | 응답에 password/hash 미포함 |
| M5 | 1 | `name` 마스킹 적용 |
| M6 | 1 | 2자 이상 마지막 글자 `*` 처리 |
| M7 | 1 | 1자 이름 `*` 처리 |

## 6.4 비밀번호 수정 (P1-P8)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| P1 | 1 | `PATCH /api/v1/users/me/password` 존재 |
| P2 | 1 | 헤더 2개 필수 검증 |
| P3 | 1 | `currentPassword/newPassword` 필수 |
| P4 | 1 | 현재 비밀번호 해시 비교 인증 |
| P5 | 1 | 새 비밀번호가 현재와 같으면 실패 |
| P6 | 0 | 회원가입 비밀번호 규칙 중 공백/제어문자 금지, `YYYY-MM-DD` 검사 미충족 |
| P7 | 1 | 새 비밀번호 해시 저장 |
| P8 | 1 | `currentPassword/newPassword`에 `trim()` 미적용 |

## 6.5 ErrorType (E1-E11)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| E1 | 1 | 500 / Internal Server Error / 메시지 일치 |
| E2 | 1 | 400 / Bad Request / 메시지 일치 |
| E3 | 1 | 404 / Not Found / 메시지 일치 |
| E4 | 1 | 409 / Conflict / 메시지 일치 |
| E5 | 1 | 409 / USER_ALREADY_EXISTS / 메시지 일치 |
| E6 | 1 | 400 / INVALID_PASSWORD_FORMAT / 메시지 일치 |
| E7 | 1 | 400 / PASSWORD_CONTAINS_BIRTHDAY / 메시지 일치 |
| E8 | 0 | 메시지가 `4~20`이 아니라 `1~20` |
| E9 | 0 | 메시지가 “공백 허용/최대 50자”와 불일치(현재 100자, 공백 미허용) |
| E10 | 1 | 400 / INVALID_EMAIL_FORMAT / 메시지 일치 |
| E11 | 1 | 400 / INVALID_BIRTHDAY / 메시지 일치 |

## 섹션 합계

| 섹션 | 통과/전체 | 섹션 점수 |
|---|---:|---:|
| 공통 | 4/6 | 6.06 |
| 회원가입 | 21/34 | 31.82 |
| 내 정보 조회 | 7/7 | 10.61 |
| 비밀번호 수정 | 7/8 | 10.61 |
| ErrorType | 9/11 | 13.64 |

## 최종
- 통과 항목: `48/66`
- 계산식: `(48/66) * 100 = 72.727...`
- 최종 점수(반올림): **73점 / 100점**
- 게이트 결과: **FAIL**
