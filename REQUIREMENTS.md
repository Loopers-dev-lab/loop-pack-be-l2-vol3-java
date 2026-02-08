# 전체 요구사항/제약사항

## 1. 공통

### 1.1 인증 헤더

사용자 정보가 필요한 모든 요청에는 아래 두 헤더를 포함해야 한다.

| 헤더 | 설명 |
|------|------|
| `X-Loopers-LoginId` | 사용자 로그인 ID |
| `X-Loopers-LoginPw` | 사용자 비밀번호 (raw) |

### 1.2 Trim 규칙

| 필드 | 규칙 |
|------|------|
| `loginId`, `name`, `email` | `trim()` 적용 후 검증. 결과가 빈 문자열이면 요청 실패 |
| `password` | `trim()` **금지**. 공백/개행/제어문자가 포함되면 요청 실패 |

### 1.3 에러 응답

- 모든 비즈니스 예외는 `CoreException` + `ErrorType` 조합으로 처리
- 흐름: `throw new CoreException(ErrorType.XXX)` → `GlobalExceptionHandler` → `ErrorResponse(code, message)`
- `@Valid` 검증 실패 → `MethodArgumentNotValidException` → BAD_REQUEST 자동 반환
- 에러 응답 필드: `code` (String), `message` (String)

---

## 2. 회원가입 (POST /api/v1/users)

### 2.1 입력 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `loginId` | String | O | 로그인 ID |
| `password` | String | O | 비밀번호 |
| `name` | String | O | 이름 |
| `birthday` | LocalDate | O | 생년월일 (yyyy-MM-dd) |
| `email` | String | O | 이메일 |

### 2.2 응답 (201 Created)

| 필드 | 타입 | 설명 |
|------|------|------|
| `id` | Long | 사용자 ID |
| `loginId` | String | 로그인 ID |
| `name` | String | 이름 |
| `birthday` | String | 생년월일 (yyyy-MM-dd) |
| `email` | String | 이메일 |

- **비밀번호/해시는 응답에 절대 포함하지 않음**

### 2.3 필드별 검증 규칙

#### loginId (로그인 ID)

| 항목 | 규칙 |
|------|------|
| 허용 문자 | 영문 대소문자 + 숫자만 (`^[a-zA-Z0-9]+$`) |
| 전처리 | `trim()` → **소문자 정규화** |
| 길이 | 4~20자 |
| 중복 검사 | 정규화된(trim + lowercase) 값 기준으로 중복 체크 |
| ErrorType | `INVALID_LOGIN_ID_FORMAT` (400) |
| 중복 시 | `USER_ALREADY_EXISTS` (409) |

#### password (비밀번호)

| 항목 | 규칙 |
|------|------|
| 길이 | 8~16자 |
| 필수 포함 | 영문 대문자(`[A-Z]`), 영문 소문자(`[a-z]`), 숫자(`[0-9]`), ASCII 특수문자 각 1개 이상 |
| 허용 문자 | 영문 대소문자 + 숫자 + ASCII 특수문자만 |
| 금지 | 공백, 개행, 제어문자 |
| trim | **금지** (입력 그대로 검증) |
| 생년월일 포함 금지 | `YYYYMMDD` (예: `19900115`), `YYYY-MM-DD` (예: `1990-01-15`) 형식 모두 체크 |
| 저장 | 해시 저장 (SHA-256 + Base64) |
| ErrorType (형식) | `INVALID_PASSWORD_FORMAT` (400) |
| ErrorType (생년월일) | `PASSWORD_CONTAINS_BIRTHDAY` (400) |

#### name (이름)

| 항목 | 규칙 |
|------|------|
| 허용 문자 | 한글, 영문, 공백 |
| 전처리 | `trim()` 적용. 빈 문자열이면 실패 |
| 길이 | 1~50자 |
| ErrorType | `INVALID_NAME_FORMAT` (400) |

#### birthday (생년월일)

| 항목 | 규칙 |
|------|------|
| 형식 | `yyyy-MM-dd` (ISO-8601) |
| 범위 | 1900-01-01 이후, 오늘 이전 (미래 날짜 금지) |
| ErrorType | `INVALID_BIRTHDAY` (400) |

#### email (이메일)

| 항목 | 규칙 |
|------|------|
| 최대 길이 | 254자 (RFC 5321) |
| 형식 | `^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$` |
| 금지 | 공백, 제어문자 |
| 도메인 추가 규칙 | 연속 점(`..`) 금지, 시작/끝 점 금지 |
| 전처리 | `trim()` 적용. 빈 문자열이면 실패 |
| ErrorType | `INVALID_EMAIL_FORMAT` (400) |

### 2.4 필수 필드 누락

- 필수 필드가 누락되면 `BAD_REQUEST` (400) 반환

---

## 3. 내 정보 조회 (GET /api/v1/users/me)

### 3.1 인증

- 필수 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

### 3.2 응답 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| `loginId` | String | 로그인 ID |
| `name` | String | 이름 (**마스킹 적용**) |
| `birthday` | String | 생년월일 (yyyy-MM-dd) |
| `email` | String | 이메일 |

- **비밀번호/해시는 응답에 절대 포함하지 않음**

### 3.3 이름 마스킹 규칙

| 이름 길이 | 규칙 | 예시 |
|-----------|------|------|
| 2자 이상 | 마지막 글자를 `*`로 대체 | `홍길동` → `홍길*`, `John` → `Joh*` |
| 1자 | 전체를 `*`로 대체 | `김` → `*` |

---

## 4. 비밀번호 수정 (PATCH /api/v1/users/me/password)

### 4.1 인증

- 필수 헤더: `X-Loopers-LoginId`, `X-Loopers-LoginPw`

### 4.2 입력 필드

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `currentPassword` | String | O | 현재 비밀번호 (raw) |
| `newPassword` | String | O | 새 비밀번호 (raw) |

### 4.3 검증 규칙

| # | 규칙 | 설명 |
|---|------|------|
| 1 | 현재 비밀번호 일치 검증 | `currentPassword`가 저장된 해시와 일치해야 함 |
| 2 | 기존 비밀번호와 동일 불가 | `newPassword` ≠ `currentPassword` |
| 3 | 회원가입과 동일한 비밀번호 규칙 | 8~16자, 영문 대소문자 + 숫자 + 특수문자 필수, 공백/개행/제어문자 금지, 생년월일 포함 금지 |
| 4 | 해시 저장 | 새 비밀번호는 해시로 저장 |
| 5 | trim 금지 | 비밀번호 필드는 `trim()` 적용하지 않음 |

---

## 5. ErrorType 정리

| ErrorType | HTTP Status | Code | Message |
|-----------|-------------|------|---------|
| `INTERNAL_ERROR` | 500 | `Internal Server Error` | `일시적인 오류가 발생했습니다.` |
| `BAD_REQUEST` | 400 | `Bad Request` | `잘못된 요청입니다.` |
| `NOT_FOUND` | 404 | `Not Found` | `존재하지 않는 요청입니다.` |
| `CONFLICT` | 409 | `Conflict` | `이미 존재하는 리소스입니다.` |
| `USER_ALREADY_EXISTS` | 409 | `USER_ALREADY_EXISTS` | `이미 가입된 로그인 ID입니다.` |
| `INVALID_PASSWORD_FORMAT` | 400 | `INVALID_PASSWORD_FORMAT` | `비밀번호는 8~16자이며, 영문 대소문자, 숫자, 특수문자를 모두 포함해야 합니다.` |
| `PASSWORD_CONTAINS_BIRTHDAY` | 400 | `PASSWORD_CONTAINS_BIRTHDAY` | `비밀번호에 생년월일을 포함할 수 없습니다.` |
| `INVALID_LOGIN_ID_FORMAT` | 400 | `INVALID_LOGIN_ID_FORMAT` | `로그인 ID는 영문과 숫자만 사용 가능하며, 4~20자여야 합니다.` |
| `INVALID_NAME_FORMAT` | 400 | `INVALID_NAME_FORMAT` | `이름은 한글, 영문, 공백만 사용 가능하며, 최대 50자입니다.` |
| `INVALID_EMAIL_FORMAT` | 400 | `INVALID_EMAIL_FORMAT` | `올바른 이메일 형식이 아닙니다.` |
| `INVALID_BIRTHDAY` | 400 | `INVALID_BIRTHDAY` | `생년월일은 1900-01-01 이후, 오늘 이전이어야 합니다.` |

---

## 6. 현재 코드와 최종 요구사항 차이 (반영 필요)

| 항목 | 현재 코드 | 최종 요구사항 |
|------|----------|-------------|
| loginId 최소 길이 | 1자 | **4자** |
| loginId 정규화 | 소문자 변환 없음 | **trim() + lowercase** |
| name 허용 문자 | 한글 + 영문 | 한글 + 영문 + **공백** |
| name 최대 길이 | 100자 | **50자** |
| password 생년월일 체크 | `YYYYMMDD` + `YYMMDD` | `YYYYMMDD` + **`YYYY-MM-DD`** |
| password 공백/제어문자 | 암묵적 | **명시적 금지 + trim 금지** |
| 내 정보 조회 API | 미구현 | **신규 구현 필요** |
| 비밀번호 수정 API | 미구현 | **신규 구현 필요** |
| 이름 마스킹 | 미구현 | **마지막 글자 `*` 대체** |
