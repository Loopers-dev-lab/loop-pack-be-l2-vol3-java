# 테스트 리스트 (TDD Red Phase용)

---

## 1. Domain Layer - MemberModel 단위 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 1.1 | 유효한 입력으로 MemberModel 생성 성공 | 모든 필드가 유효할 때 엔티티 생성 |
| 1.2 | 로그인 ID가 영문+숫자가 아니면 예외 발생 | 특수문자, 한글 등 포함 시 실패 |
| 1.3 | 비밀번호가 8자 미만이면 예외 발생 | 길이 제약 검증 |
| 1.4 | 비밀번호가 16자 초과면 예외 발생 | 길이 제약 검증 |
| 1.5 | 비밀번호에 허용되지 않은 문자 포함 시 예외 발생 | 영문대소문자+숫자+특수문자만 허용 |
| 1.6 | 비밀번호에 생년월일 포함 시 예외 발생 | 19900101 형태 포함 불가 |
| 1.7 | 이메일 형식이 유효하지 않으면 예외 발생 | 이메일 포맷 검증 |
| 1.8 | 이름이 유효하지 않으면 예외 발생 | 이름 포맷 검증 |
| 1.9 | 생년월일이 유효하지 않으면 예외 발생 | 날짜 포맷 검증 |

---

## 2. Domain Layer - 이름 마스킹 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 2.1 | 이름 마지막 글자 마스킹 (홍길동 → 홍길*) | 일반적인 마스킹 |
| 2.2 | 한 글자 이름 전체 마스킹 (A → *) | 한 글자인 경우 |
| 2.3 | 두 글자 이름 마스킹 (홍길 → 홍*) | 두 글자인 경우 |

---

## 3. Domain Layer - 비밀번호 변경 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 3.1 | 유효한 새 비밀번호로 변경 성공 | 모든 조건 충족 |
| 3.2 | 기존 비밀번호 불일치 시 예외 발생 | 현재 비밀번호 확인 실패 |
| 3.3 | 새 비밀번호가 기존과 동일하면 예외 발생 | 재사용 금지 |
| 3.4 | 새 비밀번호가 규칙 위반 시 예외 발생 | 비밀번호 RULE 검증 |
| 3.5 | 새 비밀번호에 생년월일 포함 시 예외 발생 | 생년월일 포함 불가 |

---

## 4. Domain Layer - PasswordEncoder 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 4.1 | 비밀번호 암호화 성공 | 평문 → 해시 |
| 4.2 | 암호화된 비밀번호 검증 성공 | matches 메서드 |
| 4.3 | 잘못된 비밀번호 검증 실패 | 불일치 시 false |

---

## 5. Domain Layer - MemberService 단위 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 5.1 | 회원가입 성공 | 유효한 입력으로 회원 생성 |
| 5.2 | 로그인 ID 중복 시 예외 발생 | DUPLICATE_LOGIN_ID |
| 5.3 | 회원 조회 성공 (loginId로) | 존재하는 회원 조회 |
| 5.4 | 존재하지 않는 회원 조회 시 예외 발생 | NOT_FOUND |
| 5.5 | 비밀번호 변경 성공 | 유효한 조건 충족 |
| 5.6 | 인증 성공 | loginId + loginPw 일치 |
| 5.7 | 인증 실패 (비밀번호 불일치) | UNAUTHORIZED |

---

## 6. Application Layer - MemberFacade 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 6.1 | 회원가입 Facade 정상 동작 | Service 호출 및 Info 변환 |
| 6.2 | 내 정보 조회 Facade 정상 동작 | 인증 + 조회 + 마스킹 |
| 6.3 | 비밀번호 변경 Facade 정상 동작 | 인증 + 변경 |

---

## 7. Integration Test - Repository 테스트

| # | 테스트 케이스 | 설명 |
|---|---|---|
| 7.1 | MemberModel 저장 및 조회 | JPA 저장/조회 |
| 7.2 | loginId로 회원 조회 | findByLoginId |
| 7.3 | 존재하는 loginId 중복 체크 | existsByLoginId |

---

## 8. E2E Test - 회원가입 API (POST /api/v1/members)

| # | 테스트 케이스 | 예상 응답 |
|---|---|---|
| 8.1 | 유효한 입력으로 회원가입 성공 | 200 OK + loginId |
| 8.2 | 로그인 ID 중복 시 실패 | 409 CONFLICT |
| 8.3 | 비밀번호 8자 미만 시 실패 | 400 BAD_REQUEST |
| 8.4 | 비밀번호 16자 초과 시 실패 | 400 BAD_REQUEST |
| 8.5 | 비밀번호에 생년월일 포함 시 실패 | 400 BAD_REQUEST |
| 8.6 | 이메일 형식 오류 시 실패 | 400 BAD_REQUEST |
| 8.7 | 필수 필드 누락 시 실패 | 400 BAD_REQUEST |

---

## 9. E2E Test - 내 정보 조회 API (GET /api/v1/members/me)

| # | 테스트 케이스 | 예상 응답 |
|---|---|---|
| 9.1 | 인증 성공 시 내 정보 조회 | 200 OK + 마스킹된 이름 |
| 9.2 | 인증 헤더 누락 시 실패 | 401 UNAUTHORIZED |
| 9.3 | 인증 정보 불일치 시 실패 | 401 UNAUTHORIZED |
| 9.4 | 로그인 ID 형식 위반 시 실패 | 400 BAD_REQUEST |

---

## 10. E2E Test - 비밀번호 변경 API (PATCH /api/v1/members/me/password)

| # | 테스트 케이스 | 예상 응답 |
|---|---|---|
| 10.1 | 유효한 입력으로 비밀번호 변경 성공 | 200 OK |
| 10.2 | 인증 헤더 누락 시 실패 | 401 UNAUTHORIZED |
| 10.3 | 기존 비밀번호 불일치 시 실패 | 400 BAD_REQUEST |
| 10.4 | 새 비밀번호가 기존과 동일 시 실패 | 400 BAD_REQUEST |
| 10.5 | 새 비밀번호 규칙 위반 시 실패 | 400 BAD_REQUEST |
| 10.6 | 새 비밀번호에 생년월일 포함 시 실패 | 400 BAD_REQUEST |

---

# 테스트 파일 구조 (예상)

```text
apps/commerce-api/src/test/java/com/loopers/
├── domain/member/
│   ├── MemberModelTest.java                # 1, 2, 3번 테스트
│   └── MemberServiceIntegrationTest.java   # 5, 7번 테스트
├── infrastructure/member/
│   └── BCryptPasswordEncoderTest.java      # 4번 테스트
├── application/member/
│   └── MemberFacadeTest.java               # 6번 테스트
└── interfaces/api/
    └── MemberV1ApiE2ETest.java             # 8, 9, 10번 테스트