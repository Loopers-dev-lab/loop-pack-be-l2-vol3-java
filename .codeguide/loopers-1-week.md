## 🧪 Implementation Quest

> 지정된 **단위 테스트 / 통합 테스트 / E2E 테스트 케이스**를 필수로 구현하고, 모든 테스트를 통과시키는 것을 목표로 합니다.

### 회원 가입

**🧱 단위 테스트**

- [ ]  ID 가 `영문 및 숫자 10자 이내` 형식에 맞지 않으면, User 객체 생성에 실패한다.
- [ ]  이메일이 `xx@yy.zz` 형식에 맞지 않으면, User 객체 생성에 실패한다.
- [ ]  생년월일이 `yyyy-MM-dd` 형식에 맞지 않으면, User 객체 생성에 실패한다.

**🔗 통합 테스트**

- [ ]  회원 가입시 User 저장이 수행된다. ( spy 검증 )
- [ ]  이미 가입된 ID 로 회원가입 시도 시, 실패한다.

**🌐 E2E 테스트**

- [ ]  회원 가입이 성공할 경우, 생성된 유저 정보를 응답으로 반환한다.
- [ ]  회원 가입 시에 성별이 없을 경우, `400 Bad Request` 응답을 반환한다.

### 내 정보 조회

**🔗 통합 테스트**

- [ ]  해당 ID 의 회원이 존재할 경우, 회원 정보가 반환된다.
- [ ]  해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다.

**🌐 E2E 테스트**

- [ ]  내 정보 조회에 성공할 경우, 해당하는 유저 정보를 응답으로 반환한다.
- [ ]  존재하지 않는 ID 로 조회할 경우, `404 Not Found` 응답을 반환한다.

### 포인트 조회

**🔗 통합 테스트**

- [ ]  해당 ID 의 회원이 존재할 경우, 보유 포인트가 반환된다.
- [ ]  해당 ID 의 회원이 존재하지 않을 경우, null 이 반환된다.

**🌐 E2E 테스트**

- [ ]  포인트 조회에 성공할 경우, 보유 포인트를 응답으로 반환한다.
- [ ]  `X-USER-ID` 헤더가 없을 경우, `400 Bad Request` 응답을 반환한다.

---

## 📋 구현 기록

### 1. 회원가입 기능 (`feature/sign-up`)

**구현 파일:**
| 파일 | 역할 |
|------|------|
| `MemberModel.java` | 회원 엔티티 |
| `MemberRepository.java` | Repository 인터페이스 |
| `MemberService.java` | 비즈니스 로직 (중복 검증, 비밀번호 검증, 암호화) |
| `MemberJpaRepository.java` | Spring Data JPA 인터페이스 |
| `MemberRepositoryImpl.java` | Repository 구현체 |
| `MemberV1Controller.java` | REST API 컨트롤러 |
| `MemberV1Dto.java` | 요청/응답 DTO |
| `PasswordEncoderConfig.java` | BCrypt Bean 설정 |

**설계 근거:**
- `spring-security-crypto`만 사용: 전체 Spring Security는 과한 의존성
- Layered Architecture: Domain → Infrastructure → Interface 분리
- 비밀번호 검증을 Service에 위치: PasswordEncoder 의존성 필요

**TDD 테스트 목록:**
| 테스트 | 검증 내용 |
|--------|----------|
| `register_withValidInfo_savesMember` | 정상 회원가입 |
| `register_withDuplicateLoginId_throwsException` | 로그인 ID 중복 검증 |
| `register_withShortPassword_throwsException` | 비밀번호 8자 미만 검증 |
| `register_withBirthDateInPassword_throwsException` | 생년월일 포함 검증 |
| `signUp_withValidRequest_returnsCreated` | API 201 응답 |
| `signUp_withInvalidLoginIdFormat_returnsBadRequest` | API 400 응답 |

---

### 2. 내 정보 조회 기능 (`feature/my-info`)

**구현 파일:**
| 파일 | 역할 |
|------|------|
| `AuthMember.java` | 인증 어노테이션 |
| `AuthMemberResolver.java` | 헤더 기반 인증 처리 |
| `WebMvcConfig.java` | Resolver 등록 |
| `MemberV1Dto.MyInfoResponse` | 응답 DTO (마스킹 로직 포함) |
| `MemberV1Controller.getMyInfo()` | API 추가 |
| `ErrorType.UNAUTHORIZED` | 401 에러 타입 |

**설계 근거:**
- `HandlerMethodArgumentResolver` 사용: 컨트롤러 코드 깔끔, 인증 로직 집중
- Facade 생략: 단순 조회이므로 Controller에서 직접 DTO 변환
- 마스킹 로직을 DTO에 위치: 표현 계층 관심사

**TDD 테스트 목록:**
| 테스트 | 검증 내용 |
|--------|----------|
| `myInfoResponse_masksLastCharacterOfName` | 이름 마스킹 (홍길동 → 홍길*) |
| `myInfoResponse_doesNotMaskSingleCharacterName` | 1글자 이름 마스킹 안함 |
| `getMyInfo_withoutAuthHeaders_returnsUnauthorized` | 인증 헤더 없음 401 |
| `getMyInfo_withWrongPassword_returnsUnauthorized` | 잘못된 비밀번호 401 |
| `getMyInfo_withValidAuth_returnsOkWithMaskedName` | 정상 조회 200 |

---

### 3. 비밀번호 수정 기능 (`feature/change-password`)

**구현 파일:**
| 파일 | 역할 |
|------|------|
| `MemberModel.changePassword()` | 비밀번호 변경 메서드 |
| `MemberService.changePassword()` | 검증 로직 + 암호화 |
| `MemberV1Controller.changePassword()` | PATCH API |
| `MemberV1Dto.ChangePasswordRequest` | 요청 DTO |

**설계 근거:**
- 기존 비밀번호 검증 로직 재사용 (`PASSWORD_PATTERN`, `containsBirthDate`)
- Facade 생략: 단순 흐름 (Controller → Service → Entity)

**TDD 테스트 목록:**
| 테스트 | 검증 내용 | 상태 |
|--------|----------|------|
| `changePassword_withWrongCurrentPassword_throwsException` | 현재 비밀번호 불일치 | ✅ |
| `changePassword_withSamePassword_throwsException` | 동일 비밀번호 | ✅ |
| `changePassword_withInvalidNewPassword_throwsException` | 규칙 위반 | ✅ |
| `changePassword_withBirthDateInNewPassword_throwsException` | 생년월일 포함 | ✅ |
| `changePassword_withValidInput_updatesPassword` | 정상 변경 | ✅ |
| `changePassword_withValidAuth_returnsOk` | PATCH API 200 응답 | ✅ |
