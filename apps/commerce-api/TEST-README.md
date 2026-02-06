# Commerce API Test Checklist

---

## Domain - Member

### LoginIdTest (5)
- [ ] null, 빈 문자열, 공백은 허용하지 않는다 
- [ ] 특수문자나 한글이 포함되면 생성할 수 없다 
- [ ] 영문, 숫자 조합으로 생성할 수 있다 
- [ ] 같은 값이면 동일하다 (equals)
- [ ] 다른 값이면 다르다 (equals)

### PasswordTest (13)
- [ ] null이나 빈 문자열은 허용하지 않는다 
- [ ] 유효한 비밀번호로 생성할 수 있다
- [ ] 8~16자 범위 내의 비밀번호는 허용된다 
- [ ] 8~16자 범위를 벗어나면 생성할 수 없다 
- [ ] 한글이 포함되면 생성할 수 없다
- [ ] YYYYMMDD 형식의 생년월일이 포함되면 예외가 발생한다
- [ ] YYMMDD 형식의 생년월일이 포함되어도 예외가 발생한다
- [ ] 생년월일이 포함되지 않으면 통과한다
- [ ] birthDate가 null이면 검증을 건너뛴다
- [ ] of 팩토리: 유효한 비밀번호와 생년월일로 생성할 수 있다
- [ ] of 팩토리: 형식이 잘못되면 INVALID_PASSWORD 예외가 발생한다
- [ ] of 팩토리: 생년월일이 포함된 비밀번호는 거부한다
- [ ] of 팩토리: 생년월일이 null이면 형식만 검증한다

### MemberNameTest (5)
- [ ] null, 빈 문자열, 공백은 허용하지 않는다 
- [ ] 유효한 이름으로 생성할 수 있다
- [ ] 글자 수에 따라 마지막 글자를 마스킹한다 (@CsvSource)
- [ ] 같은 값이면 동일하다 (equals)
- [ ] 다른 값이면 다르다 (equals)

### EmailTest (4)
- [ ] 유효한 이메일로 생성할 수 있다
- [ ] 이메일 형식이 올바르지 않으면 생성할 수 없다 
- [ ] 같은 값이면 동일하다 (equals)
- [ ] 다른 값이면 다르다 (equals)

### MemberModelTest (3)
- [ ] 유효한 정보로 생성할 수 있다
- [ ] email이 null이어도 생성할 수 있다
- [ ] 새 비밀번호로 변경하면 이전 비밀번호는 매칭되지 않는다

### MemberRepositoryTest (3) - Integration
- [ ] 존재하지 않는 loginId면 빈 Optional을 반환한다
- [ ] 존재하는 loginId면 저장된 회원을 반환한다
- [ ] 유효한 회원 정보를 저장하면 ID가 생성된다

---

## Domain - Member Service (Unit)

### MemberSignupServiceTest (4)
- [ ] 유효한 정보로 가입하면 비밀번호를 암호화하고 저장한다
- [ ] 이미 사용 중인 loginId면 저장하지 않고 예외를 던진다
- [ ] loginId 형식이 잘못되면 repository를 조회하지 않는다
- [ ] 비밀번호 규칙 위반이면 repository를 조회하지 않는다

### MemberAuthServiceTest (3)
- [ ] 올바른 자격 증명이면 회원을 반환한다
- [ ] 존재하지 않는 loginId면 MEMBER_NOT_FOUND 예외를 던진다
- [ ] 비밀번호가 틀리면 인증 실패 예외를 던진다

### MemberPasswordServiceTest (4)
- [ ] 유효한 요청이면 새 비밀번호로 암호화해서 저장한다
- [ ] 현재 비밀번호가 틀리면 저장하지 않고 예외를 던진다
- [ ] 새 비밀번호가 현재와 같으면 저장하지 않는다
- [ ] 새 비밀번호가 규칙에 맞지 않으면 INVALID_PASSWORD 예외를 던진다

---

## Domain - Member Service (Integration)

### MemberSignupServiceIntegrationTest (5)
- [ ] 유효한 정보로 가입하면 회원이 생성되고 비밀번호가 암호화된다
- [ ] 이미 존재하는 loginId로 가입하면 DUPLICATE_LOGIN_ID 예외가 발생한다
- [ ] loginId 형식이 잘못되면 INVALID_LOGIN_ID 예외가 발생한다
- [ ] 비밀번호 규칙을 위반하면 INVALID_PASSWORD 예외가 발생한다
- [ ] 생년월일이 포함된 비밀번호는 거부한다

### MemberAuthServiceIntegrationTest (3)
- [ ] 올바른 loginId와 비밀번호면 회원을 반환한다
- [ ] 비밀번호가 틀리면 인증 실패 예외가 발생한다
- [ ] 존재하지 않는 loginId면 MEMBER_NOT_FOUND 예외가 발생한다

### MemberPasswordServiceIntegrationTest (5)
- [ ] 올바른 현재 비밀번호와 유효한 새 비밀번호면 변경에 성공한다
- [ ] 현재 비밀번호가 틀리면 PASSWORD_MISMATCH 예외가 발생한다
- [ ] 새 비밀번호가 현재와 같으면 PASSWORD_SAME_AS_OLD 예외가 발생한다
- [ ] 새 비밀번호가 규칙에 맞지 않으면 INVALID_PASSWORD 예외가 발생한다
- [ ] 새 비밀번호에 생년월일이 포함되면 거부한다

---

## Application

### MemberFacadeTest (3)
- [ ] 회원가입: SignupService에 위임하고 MemberInfo를 반환한다
- [ ] 내 정보 조회: 인증 후 마스킹된 이름으로 반환한다
- [ ] 비밀번호 변경: 인증 후 PasswordService에 위임한다

---

## E2E (API)

### MemberV1ApiE2ETest (8)
- [ ] POST /api/v1/members - 유효한 정보로 가입하면 200과 회원 정보를 반환한다
- [ ] POST /api/v1/members - 중복 loginId로 가입하면 409를 반환한다
- [ ] POST /api/v1/members - 잘못된 loginId 형식이면 400을 반환한다
- [ ] GET /api/v1/members/me - 인증 성공 시 마스킹된 이름으로 반환한다
- [ ] GET /api/v1/members/me - 비밀번호가 틀리면 401을 반환한다
- [ ] PATCH /api/v1/members/me/password - 유효한 요청이면 200을 반환한다
- [ ] PATCH /api/v1/members/me/password - 현재 비밀번호가 틀리면 400을 반환한다
- [ ] PATCH /api/v1/members/me/password - 비밀번호 규칙을 위반하면 400을 반환한다

### ExampleV1ApiE2ETest (3)
- [ ] GET /api/v1/examples/{id} - 존재하는 ID면 해당 예시 정보를 반환한다
- [ ] GET /api/v1/examples/{id} - 숫자가 아닌 ID면 400을 반환한다
- [ ] GET /api/v1/examples/{id} - 존재하지 않는 ID면 404를 반환한다

---

## Domain - Example

### ExampleModelTest (3)
- [ ] 제목과 설명이 모두 주어지면 정상 생성된다
- [ ] 제목이 공백이면 BAD_REQUEST 예외가 발생한다
- [ ] 설명이 비어있으면 BAD_REQUEST 예외가 발생한다

### ExampleServiceIntegrationTest (2)
- [ ] 존재하는 ID면 해당 예시 정보를 반환한다
- [ ] 존재하지 않는 ID면 NOT_FOUND 예외가 발생한다

---

## Support

### CoreExceptionTest (2)
- [ ] 커스텀 메시지가 없으면 ErrorType의 메시지를 사용한다
- [ ] 커스텀 메시지가 주어지면 해당 메시지를 사용한다

---

## Context

### CommerceApiContextTest (1)
- [ ] Spring Boot 애플리케이션 컨텍스트가 정상적으로 로드된다
