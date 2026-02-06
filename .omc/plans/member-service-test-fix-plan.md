# MemberServiceTest 컴파일 오류 수정 계획 (v2)

## 1. 문제 요약

**2개 테스트 파일**에서 컴파일 오류 발생. 테스트 코드가 `MemberCommand` 객체를 사용하고 있으나, 실제 `MemberService`는 application layer DTO(`*ReqDto`)를 사용함.

## 2. 현재 상태 분석

### 2.1 영향받는 파일
| 파일 | 오류 수 | 상태 |
|------|--------|------|
| `MemberServiceTest.java` | ~24개 | Unit 테스트 |
| `MemberServiceIntegrationTest.java` | ~24개 | Integration 테스트 |

### 2.2 실제 서비스 메서드 시그니처
```java
// MemberService.java
public void addMember(AddMemberReqDto command)
public FindMemberResDto findMember(String loginId, String password)
public void putPassword(PutMemberPasswordReqDto command)
```

### 2.3 타입 불일치 상세

| 구분 | 테스트 코드 (잘못됨) | 서비스 기대 타입 |
|------|---------------------|-----------------|
| 회원가입 | `MemberCommand.SignUp` | `AddMemberReqDto` |
| 회원조회 반환 | `MemberV1Dto.MemberResponse` | `FindMemberResDto` |
| 비밀번호 변경 | `MemberCommand.ChangePassword` | `PutMemberPasswordReqDto` |

### 2.4 필드명 차이 (중요!)

**MemberCommand.ChangePassword:**
```java
public record ChangePassword(
    String loginId,
    String headerPassword,    // ← 이 필드명
    String currentPassword,
    String newPassword
) {}
```

**PutMemberPasswordReqDto:**
```java
public record PutMemberPasswordReqDto(
    String loginId,
    String loginPassword,     // ← 다른 필드명
    String currentPassword,
    String newPassword
) {}
```

### 2.5 Assertion 차이 (중요!)

**FindMemberResDto vs MemberV1Dto.MemberResponse:**
- `FindMemberResDto`: 마스킹 없이 원본 이름 반환 (`홍길동`)
- `MemberV1Dto.MemberResponse`: 마스킹된 이름 반환 (`홍길*`)

테스트 assertion 수정 필요:
```java
// 변경 전: assertThat(result.name()).isEqualTo("홍길*");
// 변경 후: assertThat(result.name()).isEqualTo("홍길동");
```

## 3. 수정 계획

### 3.1 MemberServiceTest.java 수정

#### Step 1: Import 문 수정
```java
// 삭제
import com.loopers.application.member.MemberCommand;
import com.loopers.interfaces.api.member.MemberV1Dto;

// 추가
import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
```

#### Step 2: SignUp 테스트 수정 (4개)
| 메서드 | 라인 |
|--------|------|
| `signUp_success()` | 53-55 |
| `signUp_duplicateLoginId()` | 71-73 |
| `signUp_duplicateEmail()` | 86-88 |
| `signUp_passwordEncrypted()` | 103-105 |

```java
// 변경 전
MemberCommand.SignUp command = new MemberCommand.SignUp(...)

// 변경 후
AddMemberReqDto command = new AddMemberReqDto(...)
```

#### Step 3: FindMember 테스트 수정 (1개)
| 메서드 | 라인 |
|--------|------|
| `findMember_success()` | 138, 142 |

```java
// 변경 전
MemberV1Dto.MemberResponse result = memberService.findMember(...);
assertThat(result.name()).isEqualTo("홍길*");

// 변경 후
FindMemberResDto result = memberService.findMember(...);
assertThat(result.name()).isEqualTo("홍길동");
```

#### Step 4: ChangePassword 테스트 수정 (3개)
| 메서드 | 라인 |
|--------|------|
| `changePassword_success()` | 187 |
| `changePassword_wrongCurrentPassword()` | 208 |
| `changePassword_samePassword()` | 226 |

```java
// 변경 전
MemberCommand.ChangePassword command = new MemberCommand.ChangePassword(
    "testuser", currentPassword, currentPassword, newPassword  // headerPassword
);

// 변경 후
PutMemberPasswordReqDto command = new PutMemberPasswordReqDto(
    "testuser", currentPassword, currentPassword, newPassword  // loginPassword
);
```

### 3.2 MemberServiceIntegrationTest.java 수정

#### Step 1: Import 문 수정
```java
// 삭제
import com.loopers.application.member.MemberCommand;
import com.loopers.interfaces.api.member.MemberV1Dto;

// 추가
import com.loopers.application.member.dto.AddMemberReqDto;
import com.loopers.application.member.dto.FindMemberResDto;
import com.loopers.application.member.dto.PutMemberPasswordReqDto;
```

#### Step 2: SignUp 테스트 수정 (6개)
| 메서드 | 라인 |
|--------|------|
| `signUp_success()` | 52 |
| `signUp_duplicateLoginId()` | 75, 85 |
| `signUp_duplicateEmail()` | 103, 113 |
| `findMember_success()` 내 signUp | 138 |
| `findMember_wrongPassword()` 내 signUp | 169 |
| `changePassword_success()` 내 signUp | 196 |
| `changePassword_wrongCurrentPassword()` 내 signUp | 226 |
| `changePassword_samePassword()` 내 signUp | 255 |

#### Step 3: FindMember 테스트 수정 (1개)
| 메서드 | 라인 |
|--------|------|
| `findMember_success()` | 148, 152 |

```java
// 변경 전
MemberV1Dto.MemberResponse result = memberService.findMember(...);
assertThat(result.name()).isEqualTo("홍길*");

// 변경 후
FindMemberResDto result = memberService.findMember(...);
assertThat(result.name()).isEqualTo("홍길동");
```

#### Step 4: ChangePassword 테스트 수정 (3개)
| 메서드 | 라인 |
|--------|------|
| `changePassword_success()` | 205 |
| `changePassword_wrongCurrentPassword()` | 236 |
| `changePassword_samePassword()` | 264 |

## 4. 리스크 및 완화

| 리스크 | 영향 | 완화 방안 |
|--------|------|----------|
| 필드명 차이 (`headerPassword` → `loginPassword`) | 낮음 | 필드 순서 동일, 생성자 호출 코드 변경 불필요 |
| 이름 마스킹 assertion 실패 | 중간 | `"홍길*"` → `"홍길동"` 수정 (2곳) |
| 동일 비밀번호 검증 | 중간 | `MemberValidatorUtil.validatePasswordChange()` 존재하나 Service에서 미호출 → **테스트 실패 예상** |

### 4.1 동일 비밀번호 검증 이슈 상세

**현재 상태:**
- `MemberValidatorUtil.validatePasswordChange(currentPassword, newPassword)` 메서드 존재 (line 102-106)
- 하지만 `MemberService.putPassword()`에서 이 메서드를 호출하지 않음

**예상 결과:**
- `changePassword_samePassword()` 테스트는 `BAD_REQUEST` 예외를 기대하지만 실패할 가능성 있음

**권장 조치:**
1. **옵션 A**: `MemberService.putPassword()`에 검증 로직 추가 (권장)
2. **옵션 B**: 테스트 기대값 수정 (테스트가 현재 서비스 동작을 반영)

## 5. 검증 단계

```bash
# 1. 테스트 코드 컴파일 확인
./gradlew :apps:commerce-api:compileTestJava

# 2. Unit 테스트 실행
./gradlew :apps:commerce-api:test --tests "MemberServiceTest"

# 3. Integration 테스트 실행
./gradlew :apps:commerce-api:test --tests "MemberServiceIntegrationTest"

# 4. 전체 테스트 실행
./gradlew :apps:commerce-api:test
```

## 6. 예상 결과

### 컴파일
- 오류: ~48개 → 0개

### 테스트 (동일 비밀번호 검증 이슈 해결 후)
- `MemberServiceTest`: 8/8 통과
- `MemberServiceIntegrationTest`: 12/12 통과

## 7. 작업 범위 요약

| 항목 | 수량 |
|------|------|
| 수정 파일 | 2개 (+ 선택적으로 MemberService 1개) |
| 수정 라인 | ~50줄 |
| 영향 테스트 | 20개 |
| 다른 기능 영향 | 없음 |

## 8. MemberCommand 처리 방안

`MemberCommand`는 현재 다른 곳에서 사용되지 않음 (테스트에서만 잘못 참조 중).
- **권장**: 테스트 수정 후 `MemberCommand.java` 삭제 검토
- **대안**: 향후 사용을 위해 유지 (단, 현재는 dead code)
