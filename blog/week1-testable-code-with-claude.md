# 검증 로직을 어디에 둘 것인가 — VO 자가 검증으로 테스트 용이한 구조 만들기

## 들어가며

"검증 로직은 어디에 두는 게 좋을까?"

회원가입 기능을 구현한다고 생각해보자. ID는 영문/숫자 10자 이내, 이메일은 `xxx@yyy.zzz` 형식, 비밀번호는 최소 8자에 영문/숫자/특수문자 포함. 이 검증 로직은 **어디**에 위치해야 할까?

전통적인 방식은 Service에 모든 검증을 집중시킨다. 그리고 나는 이번 과제에서 그 방식의 문제점을 경험했다.

---

## 문제: Service에 검증이 집중되면 Mock 지옥

```java
// Service에 검증이 집중된 구조
public class MemberService {

    public Member register(String loginId, String email, String password, ...) {
        // 검증 로직들
        if (loginId == null || !loginId.matches("^[A-Za-z0-9]{1,10}$")) {
            throw new IllegalArgumentException("ID 형식 오류");
        }
        if (email == null || !email.matches("^[\\w-.]+@[\\w-]+(\\.[a-z]{2,})+$")) {
            throw new IllegalArgumentException("이메일 형식 오류");
        }
        // ... 비밀번호 검증, 기타 검증들

        // 비즈니스 로직
        Member member = new Member(loginId, email, password);
        return memberRepository.save(member);
    }
}
```

이 구조에서 "ID 형식 검증"만 테스트하려면 어떻게 해야 할까?

```java
@Test
void register_withInvalidLoginId_throwsException() {
    // Service의 모든 의존성을 준비해야 함
    MemberRepository mockRepository = mock(MemberRepository.class);
    PasswordEncoder mockEncoder = mock(PasswordEncoder.class);
    MemberService service = new MemberService(mockRepository, mockEncoder);

    // 그제서야 검증 테스트 가능
    assertThatThrownBy(() -> service.register("user!@#", ...))
        .isInstanceOf(IllegalArgumentException.class);
}
```

**ID 형식 검증 하나를 테스트하는데 Repository와 PasswordEncoder를 Mock해야 한다.** 검증 로직이 늘어날수록 테스트 셋업 비용도 늘어난다.

---

## 해결: 검증 로직을 VO에 위임

검증 로직의 위치를 바꾸면 테스트 구조가 완전히 달라진다.

```
┌─────────────────────────────────┐    ┌─────────────────────────────────┐
│     Before: Service 집중         │    │     After: VO 자가 검증           │
├─────────────────────────────────┤    ├─────────────────────────────────┤
│                                 │    │                                 │
│  Controller                     │    │  Controller                     │
│      │                          │    │      │                          │
│      ▼                          │    │      ▼                          │
│  Service ◀── 검증 + 비즈니스 로직    │    │  Service ◀── 비즈니스 로직만       │
│      │                          │    │      │                          │
│      ├──▶ Repository (Mock 필수) │    │      ├──▶ VO 생성                │
│      │                          │    │      │      └──▶ 자가 검증        │
│      └──▶ PasswordEncoder       │    │      │                          │
│           (Mock 필수)            │    │      └──▶ Repository            │
│                                 │    │                                 │
└─────────────────────────────────┘    └─────────────────────────────────┘

  ❌ 검증 테스트 = Mock 지옥           ✅ new LoginId("abc") 만으로 테스트
```

핵심 아이디어는 **"유효하지 않은 객체는 존재할 수 없다"**는 원칙이다. VO가 생성되는 시점에 스스로 검증하고, 유효하지 않으면 예외를 던진다.

---

## 구현: 자가 검증 VO

### LoginId

```java
@Embeddable
public class LoginId {

    private static final Pattern PATTERN = Pattern.compile("^[A-Za-z0-9]{1,10}$");

    @Column(name = "login_id", nullable = false, unique = true, length = 20)
    private String value;

    protected LoginId() {}  // JPA용

    public LoginId(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "ID는 영문 및 숫자 10자 이내여야 합니다.");
        }
        this.value = value;
    }

    public String value() { return value; }

    // equals, hashCode 생략
}
```

### Email

```java
@Embeddable
public class Email {

    private static final Pattern PATTERN =
        Pattern.compile("^[\\w-.]+@[\\w-]+(\\.[a-z]{2,})+$");

    @Column(name = "email", nullable = false, length = 100)
    private String value;

    protected Email() {}

    public Email(String value) {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "올바른 이메일 형식이 아닙니다.");
        }
        this.value = value;
    }

    // ...
}
```

---

## 테스트가 이렇게 단순해진다

```java
class LoginIdTest {

    @Test
    void create_withValidFormat_succeeds() {
        LoginId loginId = new LoginId("user1234");
        assertThat(loginId.value()).isEqualTo("user1234");
    }

    @Test
    void create_withSpecialChars_throwsException() {
        assertThatThrownBy(() -> new LoginId("user!@#"))
            .isInstanceOf(CoreException.class);
    }

    @Test
    void create_withTooLong_throwsException() {
        assertThatThrownBy(() -> new LoginId("abcdefghijk"))
            .isInstanceOf(CoreException.class);
    }

    @Test
    void create_withNull_throwsException() {
        assertThatThrownBy(() -> new LoginId(null))
            .isInstanceOf(CoreException.class);
    }
}
```

**Mock이 없다. 의존성이 없다.** `new LoginId("user!@#")` 한 줄로 도메인 규칙을 테스트한다.

이런 단위 테스트는:
- 실행 속도가 빠르다 (Spring Context 불필요)
- 실패 원인이 명확하다 (LoginId 규칙 위반)
- 격리되어 있다 (다른 코드 변경에 영향받지 않음)

---

## 트레이드오프: record vs class

Java의 `record`는 VO에 적합해 보인다. `equals`, `hashCode`, `toString`이 자동 생성되기 때문이다.

```java
// record로 구현하면 깔끔할 것 같지만...
public record LoginId(String value) {
    public LoginId {
        if (value == null || !PATTERN.matcher(value).matches()) {
            throw new CoreException(...);
        }
    }
}
```

하지만 JPA `@Embeddable`과 함께 사용할 때 문제가 있다:

1. **기본 생성자 필수**: JPA는 리플렉션으로 객체를 생성하므로 기본 생성자가 필요한데, record는 canonical 생성자만 가진다
2. **QueryDSL 호환성**: Q-class 생성 시 record와 충돌이 발생할 수 있다

결국 `class`로 구현하고 `equals`/`hashCode`를 직접 작성했다. 보일러플레이트 코드가 늘어나는 비용이 있지만, JPA/QueryDSL과의 안정적인 통합을 선택했다.

```java
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LoginId loginId)) return false;
    return Objects.equals(value, loginId.value);
}

@Override
public int hashCode() { return Objects.hash(value); }
```

---

## 결론

**검증 로직을 VO에 위임하면 `new LoginId("abc")`만으로 도메인 규칙을 테스트할 수 있다.**

테스트하기 어려운 코드는 대체로 설계에 문제가 있다는 신호다. 반대로 말하면, **테스트 용이한 구조를 추구하다 보면 자연스럽게 좋은 설계에 도달한다**.

이번 과제에서 VO 자가 검증 패턴을 적용한 결과, 52개 테스트(단위 39 / 통합 7 / E2E 6) 중 단위 테스트가 가장 많은 비중을 차지했다. Mock 없이 빠르게 실행되는 단위 테스트가 많다는 것은 도메인 로직이 적절히 분리되어 있다는 증거이기도 하다.

```
        테스트 피라미드
        ──────────────
              /\
             /  \      E2E: 6개
            /────\
           /      \    통합: 7개
          /────────\
         /          \  단위: 39개
        /────────────\
```

"테스트하기 쉬운 구조 = 좋은 설계"라는 등식을 믿고, 다음 과제에서도 이 원칙을 적용해볼 생각이다.
