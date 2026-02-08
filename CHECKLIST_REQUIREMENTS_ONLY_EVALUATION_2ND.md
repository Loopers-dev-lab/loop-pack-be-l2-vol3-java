# CHECKLIST_REQUIREMENTS_ONLY 2차 평가 결과

## 평가 기준
- 기준 문서: `CHECKLIST_REQUIREMENTS_ONLY.md`
- 총 항목: 66개
- 채점 방식: 게이트형(각 항목 `1` 또는 `0`)
- 점수 계산식: `(통과 항목 수 / 66) * 100`

## 1) 공통 (C1-C6)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| C1 | 1 | 사용자 정보 API(내 정보 조회/비밀번호 수정)에서 `X-Loopers-LoginId`, `X-Loopers-LoginPw`를 모두 받아 검증 |
| C2 | 1 | `loginId`, `name`, `email`은 `trim()` 후 검증하며 blank면 실패 처리 |
| C3 | 1 | password는 `trim()` 없이 검증하고, 공백/개행/제어문자를 허용하지 않도록 차단 |
| C4 | 1 | 비즈니스 예외를 `CoreException + ErrorType` 조합으로 처리 |
| C5 | 1 | `CoreException -> GlobalExceptionHandler -> ErrorResponse(code, message)` 흐름 충족 |
| C6 | 1 | `@Valid` 실패를 `BAD_REQUEST(400)`로 반환하며 `code`, `message` 포함 |

## 2) 회원가입 (S1-S34)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| S1 | 1 | `POST /api/v1/users` 엔드포인트 존재 |
| S2 | 1 | `loginId`, `password`, `name`, `birthday`, `email` 필수 검증 존재 |
| S3 | 1 | 성공 시 `201 Created` 반환 |
| S4 | 1 | 성공 응답에 `id`, `loginId`, `name`, `birthday`, `email` 반환 |
| S5 | 1 | 성공 응답에 비밀번호/해시 미포함 |
| S6 | 1 | `loginId`를 영문/숫자 정규식으로 검증 |
| S7 | 1 | `loginId`를 `trim()` 후 소문자 정규화 |
| S8 | 1 | `loginId` 길이 `4~20` 검증 |
| S9 | 1 | `loginId` 중복 검사를 정규화 값 기준으로 수행 |
| S10 | 1 | `loginId` 형식 위반 시 `INVALID_LOGIN_ID_FORMAT(400)` |
| S11 | 1 | `loginId` 중복 시 `USER_ALREADY_EXISTS(409)` |
| S12 | 1 | `password` 길이 `8~16` 검증 |
| S13 | 1 | `password`에 대문자/소문자/숫자/특수문자 각 1개 이상 검증 |
| S14 | 1 | `password` 허용 문자를 영문 대소문자+숫자+ASCII 특수문자 집합으로 제한 |
| S15 | 1 | `password` 공백/개행/제어문자 허용하지 않음 |
| S16 | 1 | `password`에 `trim()` 적용하지 않음 |
| S17 | 1 | `password`에 생년월일 포함 금지(`YYYYMMDD`, `YYYY-MM-DD`) 검사 |
| S18 | 1 | 비밀번호를 `SHA-256 + Base64` 해시로 저장 |
| S19 | 1 | `password` 형식 위반 시 `INVALID_PASSWORD_FORMAT(400)` |
| S20 | 1 | `password` 생년월일 포함 시 `PASSWORD_CONTAINS_BIRTHDAY(400)` |
| S21 | 1 | `name` 허용 문자를 한글/영문/공백으로 검증 |
| S22 | 1 | `name`을 `trim()` 후 blank면 실패 처리 |
| S23 | 1 | `name` 길이 `1~50` 검증 |
| S24 | 1 | `name` 형식 위반 시 `INVALID_NAME_FORMAT(400)` |
| S25 | 1 | `birthday` 형식 `yyyy-MM-dd`(ISO-8601) 경로 충족 |
| S26 | 1 | `birthday`를 `1900-01-01` 이후, 오늘 이전으로 검증 |
| S27 | 1 | `birthday` 위반 시 `INVALID_BIRTHDAY(400)` |
| S28 | 1 | `email` 최대 길이 `254` 검증 |
| S29 | 1 | `email` 기본 형식 정규식 검증 |
| S30 | 1 | `email`에서 공백/제어문자를 허용하지 않음 |
| S31 | 1 | `email` 도메인에 연속 점/시작 점/끝 점 금지 규칙 적용 |
| S32 | 1 | `email`을 `trim()` 후 blank면 실패 처리 |
| S33 | 1 | `email` 형식 위반 시 `INVALID_EMAIL_FORMAT(400)` |
| S34 | 1 | 회원가입 필수 필드 누락 시 `BAD_REQUEST(400)` 반환 |

## 3) 내 정보 조회 (M1-M7)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| M1 | 1 | `GET /api/v1/users/me` 엔드포인트 존재 |
| M2 | 1 | 요청 헤더 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 필수 검증 |
| M3 | 1 | 응답 필드 `loginId`, `name`, `birthday`, `email` 반환 |
| M4 | 1 | 응답에 비밀번호/해시 미포함 |
| M5 | 1 | `name` 마스킹 규칙 적용 |
| M6 | 1 | 이름 길이 2자 이상일 때 마지막 글자를 `*` 처리 |
| M7 | 1 | 이름 길이 1자일 때 `*` 반환 |

## 4) 비밀번호 수정 (P1-P8)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| P1 | 1 | `PATCH /api/v1/users/me/password` 엔드포인트 존재 |
| P2 | 1 | 요청 헤더 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 필수 검증 |
| P3 | 1 | 요청 필드 `currentPassword`, `newPassword` 필수 검증 |
| P4 | 1 | `currentPassword`를 저장된 해시와 비교해 일치 검증 |
| P5 | 1 | `newPassword == currentPassword`이면 실패 처리 |
| P6 | 1 | `newPassword`에 회원가입과 동일한 비밀번호 규칙 적용 |
| P7 | 1 | 새 비밀번호를 해시로 저장 |
| P8 | 1 | `currentPassword`, `newPassword`에 `trim()` 미적용 |

## 5) ErrorType (E1-E11)

| ID | 결과 | 판정 사유 |
|---|---:|---|
| E1 | 1 | `INTERNAL_ERROR = 500 / Internal Server Error / 일시적인 오류가 발생했습니다.` 일치 |
| E2 | 1 | `BAD_REQUEST = 400 / Bad Request / 잘못된 요청입니다.` 일치 |
| E3 | 1 | `NOT_FOUND = 404 / Not Found / 존재하지 않는 요청입니다.` 일치 |
| E4 | 1 | `CONFLICT = 409 / Conflict / 이미 존재하는 리소스입니다.` 일치 |
| E5 | 1 | `USER_ALREADY_EXISTS = 409 / USER_ALREADY_EXISTS / 이미 가입된 로그인 ID입니다.` 일치 |
| E6 | 1 | `INVALID_PASSWORD_FORMAT = 400 / INVALID_PASSWORD_FORMAT / 비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.` 일치 |
| E7 | 1 | `PASSWORD_CONTAINS_BIRTHDAY = 400 / PASSWORD_CONTAINS_BIRTHDAY / 비밀번호에 생년월일을 포함할 수 없습니다.` 일치 |
| E8 | 1 | `INVALID_LOGIN_ID_FORMAT = 400 / INVALID_LOGIN_ID_FORMAT / 로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다.` 일치 |
| E9 | 1 | `INVALID_NAME_FORMAT = 400 / INVALID_NAME_FORMAT / 이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다.` 일치 |
| E10 | 1 | `INVALID_EMAIL_FORMAT = 400 / INVALID_EMAIL_FORMAT / 올바른 이메일 형식이 아닙니다.` 일치 |
| E11 | 1 | `INVALID_BIRTHDAY = 400 / INVALID_BIRTHDAY / 생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.` 일치 |

## 섹션 합계

| 섹션 | 통과/전체 |
|---|---:|
| 공통 | 6/6 |
| 회원가입 | 34/34 |
| 내 정보 조회 | 7/7 |
| 비밀번호 수정 | 8/8 |
| ErrorType | 11/11 |

## 최종 결과 (2차)
- 통과 항목: `66/66`
- 최종 점수: **100점 / 100점**
- 게이트 결과: **PASS**
