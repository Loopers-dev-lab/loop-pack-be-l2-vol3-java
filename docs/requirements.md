# User API 요구사항 명세서

## 1. 회원가입

### API
- **POST** `/api/v1/users`
- **Status**: 201 Created

### 요청 정보
| 필드 | 타입 | 필수 | 검증 규칙 |
|------|------|------|----------|
| loginId | String | O | 4~20자, 영문소문자+숫자만 |
| password | String | O | 8~16자, 대/소문자+숫자+특수문자 필수, 생년월일 미포함 |
| name | String | O | 2~10자, 한글만 |
| email | String | O | 이메일 형식 |
| birthDate | String | O | yyyy-MM-dd, 과거 날짜만 |

### 비즈니스 규칙
- 이미 가입된 로그인 ID로는 가입 불가 (409 Conflict)
- 비밀번호는 BCrypt로 암호화하여 저장

### 비밀번호 규칙
1. 8~16자의 영문 대소문자, 숫자, 특수문자만 가능
2. 대문자, 소문자, 숫자, 특수문자 각 1개 이상 필수
3. 생년월일은 비밀번호 내에 포함될 수 없음 (yyMMdd, MMdd, ddMM 형식)

---

## 2. 내 정보 조회

### API
- **GET** `/api/v1/users/me`
- **Status**: 200 OK

### 인증 헤더
| 헤더 | 설명 |
|------|------|
| X-Loopers-LoginId | 로그인 ID |
| X-Loopers-LoginPw | 비밀번호 |

### 응답 정보
| 필드 | 타입 | 설명 |
|------|------|------|
| loginId | String | 로그인 ID |
| name | String | 이름 (마지막 글자 마스킹) |
| email | String | 이메일 |
| birthDate | String | 생년월일 (yyyy-MM-dd) |

### 비즈니스 규칙
- 이름은 마지막 글자를 `*`로 마스킹하여 반환
  - 예: "홍길동" → "홍길*"
- 인증 실패 시 401 Unauthorized

---

## 3. 비밀번호 수정

### API
- **PATCH** `/api/v1/users/me/password`
- **Status**: 200 OK

### 인증 헤더
| 헤더 | 설명 |
|------|------|
| X-Loopers-LoginId | 로그인 ID |
| X-Loopers-LoginPw | 현재 비밀번호 |

### 요청 정보
| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| currentPassword | String | O | 현재 비밀번호 (헤더와 동일) |
| newPassword | String | O | 새 비밀번호 |

### 비즈니스 규칙
- 비밀번호 규칙 준수 (회원가입과 동일)
- 현재 비밀번호와 동일한 비밀번호로 변경 불가 (400 Bad Request)
- 인증 실패 시 401 Unauthorized

---

## 공통 에러 응답

| Status | ErrorType | 설명 |
|--------|-----------|------|
| 400 | BAD_REQUEST | 유효성 검증 실패 |
| 401 | UNAUTHORIZED | 인증 실패 |
| 409 | CONFLICT | 중복 리소스 |
| 500 | INTERNAL_ERROR | 서버 오류 |
