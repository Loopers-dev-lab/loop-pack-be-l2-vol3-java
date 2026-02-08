# REQUIREMENTS 기반 최종 체크리스트 및 게이트형 채점 기준 (v1)

## 1. 평가 철학

- 평가는 게이트형으로만 수행한다: `충족(1)` 또는 `미충족(0)`.
- 부분충족(0.5)은 허용하지 않는다.
- 체크리스트 한 줄이 복합 조건이면 모두 만족해야 `1`.
- 구현이 없거나 반대 동작이거나 증거가 부족하면 `0`.
- 목적은 `REQUIREMENTS.md`에 명시된 요구사항 충족 여부만 판정하는 것이다.

## 2. 점수 구조

- 평가 단위: 체크박스 `66개`.
- 항목당 점수: `100 / 66 = 1.515151...점`.
- 섹션별 배점(반올림):
  - 공통(6개): `9.09점`
  - 회원가입(34개): `51.52점`
  - 내 정보 조회(7개): `10.61점`
  - 비밀번호 수정(8개): `12.12점`
  - ErrorType(11개): `16.67점`

## 3. 점수 계산 및 게이트 판정

- `finalScore = (통과 항목 수 / 66) * 100`
- 최종 제시 점수는 마지막에 반올림한다.
- 게이트 결과:
  - `PASS`: `66/66` 전부 통과
  - `FAIL`: 하나라도 미통과

## 4. 증거 채택 규칙

- 구현 증거 우선: `Controller -> Service/Facade -> Domain -> Repository/Entity` 실행 경로를 본다.
- 문구/주석/테스트 이름만으로는 충족 인정하지 않는다.
- API 계약(요청/헤더/응답 필드명/에러 구조)이 요구와 다르면 미충족이다.
- 에러 처리는 상태코드와 응답 스키마가 요구사항과 일치해야 충족이다.

## 5. 항목 판정 규칙

- 각 항목은 아래 체크박스 그대로 `1/0`으로 판정한다.
- 한 항목 안에 `A + B + C`가 있으면 A/B/C 모두 필요하다.
- `존재` 요구는 누락 시 반드시 실패해야 충족이다.
- `금지` 요구는 금지 입력이 실제로 차단되어야 충족이다.
- `포함 금지` 요구는 응답/저장소/로그 등 노출 경로 전반에서 없어야 충족이다.

## 6. 게이트 체크리스트 (총 66개)

### 6.1 공통 (C1-C6)

- [ ] `C1` 사용자 정보가 필요한 요청에는 `X-Loopers-LoginId`, `X-Loopers-LoginPw` 헤더가 모두 존재
- [ ] `C2` `loginId`, `name`, `email`은 `trim()` 후 검증하며 결과가 빈 문자열이면 실패
- [ ] `C3` `password`는 `trim()` 금지, 공백/개행/제어문자 포함 시 실패
- [ ] `C4` 모든 비즈니스 예외는 `CoreException + ErrorType` 조합으로 처리
- [ ] `C5` 예외 처리 흐름이 `throw CoreException(ErrorType.XXX) -> GlobalExceptionHandler -> ErrorResponse(code, message)`를 충족
- [ ] `C6` `@Valid` 검증 실패는 `BAD_REQUEST(400)`로 반환되며 에러 응답 필드에 `code`, `message`가 존재

### 6.2 회원가입 (S1-S34)

- [ ] `S1` `POST /api/v1/users` 엔드포인트 존재
- [ ] `S2` 요청 필드 `loginId`, `password`, `name`, `birthday`, `email`가 모두 필수
- [ ] `S3` 성공 시 `201 Created` 반환
- [ ] `S4` 성공 응답 필드 `id`, `loginId`, `name`, `birthday`, `email` 반환
- [ ] `S5` 성공 응답에 비밀번호/해시를 포함하지 않음
- [ ] `S6` `loginId` 허용 문자: 영문 대소문자 + 숫자만(`^[a-zA-Z0-9]+$`)
- [ ] `S7` `loginId` 전처리: `trim()` 후 소문자 정규화
- [ ] `S8` `loginId` 길이 `4~20`
- [ ] `S9` `loginId` 중복 검사는 정규화된 값(trim + lowercase) 기준
- [ ] `S10` `loginId` 형식 위반 시 `INVALID_LOGIN_ID_FORMAT(400)` 처리
- [ ] `S11` `loginId` 중복 시 `USER_ALREADY_EXISTS(409)` 처리
- [ ] `S12` `password` 길이 `8~16`
- [ ] `S13` `password` 필수 포함: 영문 대문자/소문자/숫자/ASCII 특수문자 각 1개 이상
- [ ] `S14` `password` 허용 문자: 영문 대소문자 + 숫자 + ASCII 특수문자만
- [ ] `S15` `password` 공백/개행/제어문자 금지
- [ ] `S16` `password`는 `trim()` 적용 금지
- [ ] `S17` `password`에 생년월일 포함 금지(`YYYYMMDD`, `YYYY-MM-DD`)
- [ ] `S18` `password` 저장 시 해시 사용(`SHA-256 + Base64`)
- [ ] `S19` `password` 형식 위반 시 `INVALID_PASSWORD_FORMAT(400)` 처리
- [ ] `S20` `password` 생년월일 포함 시 `PASSWORD_CONTAINS_BIRTHDAY(400)` 처리
- [ ] `S21` `name` 허용 문자: 한글, 영문, 공백
- [ ] `S22` `name`은 `trim()` 적용 후 빈 문자열이면 실패
- [ ] `S23` `name` 길이 `1~50`
- [ ] `S24` `name` 형식 위반 시 `INVALID_NAME_FORMAT(400)` 처리
- [ ] `S25` `birthday` 형식은 `yyyy-MM-dd`(ISO-8601)
- [ ] `S26` `birthday` 범위는 `1900-01-01` 이후, 오늘 이전
- [ ] `S27` `birthday` 위반 시 `INVALID_BIRTHDAY(400)` 처리
- [ ] `S28` `email` 최대 길이 `254`
- [ ] `S29` `email` 형식은 `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$`
- [ ] `S30` `email` 공백/제어문자 금지
- [ ] `S31` `email` 도메인 추가 규칙: 연속 점(`..`) 금지, 시작/끝 점 금지
- [ ] `S32` `email`은 `trim()` 적용 후 빈 문자열이면 실패
- [ ] `S33` `email` 형식 위반 시 `INVALID_EMAIL_FORMAT(400)` 처리
- [ ] `S34` 회원가입 필수 필드 누락 시 `BAD_REQUEST(400)` 반환

### 6.3 내 정보 조회 (M1-M7)

- [ ] `M1` `GET /api/v1/users/me` 엔드포인트 존재
- [ ] `M2` 요청 헤더 `X-Loopers-LoginId`, `X-Loopers-LoginPw`가 모두 필수
- [ ] `M3` 응답 필드 `loginId`, `name`, `birthday`, `email` 반환
- [ ] `M4` 응답에 비밀번호/해시를 포함하지 않음
- [ ] `M5` `name` 필드에 마스킹 규칙이 적용됨
- [ ] `M6` 이름 길이 2자 이상: 마지막 글자를 `*`로 대체
- [ ] `M7` 이름 길이 1자: 전체를 `*`로 대체

### 6.4 비밀번호 수정 (P1-P8)

- [ ] `P1` `PATCH /api/v1/users/me/password` 엔드포인트 존재
- [ ] `P2` 요청 헤더 `X-Loopers-LoginId`, `X-Loopers-LoginPw`가 모두 필수
- [ ] `P3` 요청 필드 `currentPassword`, `newPassword`가 모두 필수
- [ ] `P4` `currentPassword`는 저장된 해시와 일치해야 함
- [ ] `P5` `newPassword`는 `currentPassword`와 동일하면 실패
- [ ] `P6` `newPassword`는 회원가입과 동일한 비밀번호 규칙(길이/문자조합/공백·개행·제어문자 금지/생년월일 포함 금지)을 적용
- [ ] `P7` 새 비밀번호는 해시로 저장
- [ ] `P8` 비밀번호 필드(`currentPassword`, `newPassword`)는 `trim()` 적용 금지

### 6.5 ErrorType 정리 (E1-E11)

- [ ] `E1` `INTERNAL_ERROR` = `500 / Internal Server Error / 일시적인 오류가 발생했습니다.`
- [ ] `E2` `BAD_REQUEST` = `400 / Bad Request / 잘못된 요청입니다.`
- [ ] `E3` `NOT_FOUND` = `404 / Not Found / 존재하지 않는 요청입니다.`
- [ ] `E4` `CONFLICT` = `409 / Conflict / 이미 존재하는 리소스입니다.`
- [ ] `E5` `USER_ALREADY_EXISTS` = `409 / USER_ALREADY_EXISTS / 이미 가입된 로그인 ID입니다.`
- [ ] `E6` `INVALID_PASSWORD_FORMAT` = `400 / INVALID_PASSWORD_FORMAT / 비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.`
- [ ] `E7` `PASSWORD_CONTAINS_BIRTHDAY` = `400 / PASSWORD_CONTAINS_BIRTHDAY / 비밀번호에 생년월일을 포함할 수 없습니다.`
- [ ] `E8` `INVALID_LOGIN_ID_FORMAT` = `400 / INVALID_LOGIN_ID_FORMAT / 로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다.`
- [ ] `E9` `INVALID_NAME_FORMAT` = `400 / INVALID_NAME_FORMAT / 이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다.`
- [ ] `E10` `INVALID_EMAIL_FORMAT` = `400 / INVALID_EMAIL_FORMAT / 올바른 이메일 형식이 아닙니다.`
- [ ] `E11` `INVALID_BIRTHDAY` = `400 / INVALID_BIRTHDAY / 생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.`

## 7. 섹션 운영 규칙

- `ErrorType` 항목은 `HTTP Status + Code + Message` 3요소가 모두 일치해야 충족이다.
- 회원가입/내 정보 조회/비밀번호 수정 항목은 각 API 단위로 독립 채점한다.
- 공통 규칙 위반은 관련 API 섹션 항목과 별개로 공통 섹션에서 추가 감점한다.

## 8. 리포트 작성 형식 (평가 시)

- 항목별로 `ID / 결과(1|0) / 근거 파일:라인 / 판정 사유`를 기록한다.
- 섹션 합계와 총점을 함께 제시한다.
- 마지막에 게이트 결과를 `PASS` 또는 `FAIL`로 명시한다.
