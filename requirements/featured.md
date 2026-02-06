## 1. Member 엔티티 필드

| 필드 | 타입 | 설명 | 제약조건 |
|---|---|---|---|
| loginId | String | 로그인 ID (고유) | 영문+숫자만 허용, 중복 불가 |
| loginPw | String | 비밀번호 | 암호화 저장, 8~16자, 영문대소문자+숫자+특수문자 |
| name | String | 이름 | 포맷 검증 필요 |
| birthDate | LocalDate | 생년월일 | YYYY-MM-DD 형식 |
| email | String | 이메일 | 이메일 포맷 검증 |

---

## 2. API 목록

| 기능 | Method | Endpoint | 인증 |
|---|---|---|---|
| 회원가입 | POST | /api/v1/members | 불필요 |
| 내 정보 조회 | GET | /api/v1/members/me | 필요 (헤더) |
| 비밀번호 수정 | PATCH | /api/v1/members/me/password | 필요 (헤더) |

인증 헤더:
- X-Loopers-LoginId: 로그인 ID
- X-Loopers-LoginPw: 비밀번호

---

## 3. 기능별 상세

### 3.1 회원가입

- 입력: loginId, loginPw, name, birthDate, email
- 검증 규칙:
    - loginId 중복 검사
    - 비밀번호: 8~16자, 영문대소문자+숫자+특수문자만 허용
    - 비밀번호에 생년월일 포함 불가 (예: 19900101)
    - 이메일/이름/생년월일 포맷 검증
- 응답: 성공 여부 + loginId 반환

### 3.2 내 정보 조회

- 입력: 인증 헤더
- 응답: loginId, name(마스킹), birthDate, email
- 마스킹 규칙: 이름 마지막 글자를 *로 치환 (예: 홍길동 → 홍길*)

### 3.3 비밀번호 수정

- 입력: 기존 비밀번호, 새 비밀번호
- 검증 규칙:
    - 기존 비밀번호 일치 확인
    - 새 비밀번호 ≠ 기존 비밀번호
    - 비밀번호 규칙 준수 (생년월일 포함 불가)

---

## 4. 구현 필요 컴포넌트

```
com.loopers
├── interfaces/api/member/
│   ├── MemberV1Controller.java
│   ├── MemberV1ApiSpec.java
│   └── MemberV1Dto.java
├── application/member/
│   ├── MemberFacade.java
│   └── MemberInfo.java
├── domain/member/
│   ├── MemberModel.java          # Entity
│   ├── MemberService.java        # 도메인 서비스
│   ├── MemberRepository.java     # Repository 인터페이스
│   └── PasswordEncoder.java      # 비밀번호 암호화 인터페이스
├── infrastructure/member/
│   ├── MemberJpaRepository.java
│   ├── MemberRepositoryImpl.java
│   └── BCryptPasswordEncoder.java
└── support/error/
└── ErrorType.java            # 에러 타입 추가
```

---

## 5. 추가 필요 ErrorType

| ErrorType | HttpStatus | 설명 |
|---|---|---|
| DUPLICATE_LOGIN_ID | 409 CONFLICT | 로그인 ID 중복 |
| INVALID_PASSWORD | 400 BAD_REQUEST | 비밀번호 규칙 위반 |
| UNAUTHORIZED | 401 UNAUTHORIZED | 인증 실패 |
| PASSWORD_MISMATCH | 400 BAD_REQUEST | 기존 비밀번호 불일치 |
| SAME_PASSWORD | 400 BAD_REQUEST | 새 비밀번호가 기존과 동일 |

---