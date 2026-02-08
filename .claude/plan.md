# AI 협업 개발 가이드

본 문서는 AI 도구(Claude Code)와의 효과적인 협업을 위한 개발 원칙을 정의합니다.

## 1. 협업 철학 - 증강 코딩 (Augmented Coding)

AI는 개발자의 역량을 증강하는 도구이며, 의사결정의 주체는 개발자입니다.

- **제안과 승인**: AI는 방향성과 대안을 제안하고, 개발자가 최종 승인
- **설계 주도권**: 아키텍처와 설계 결정은 개발자가 주도
- **중간 개입**: 반복 동작, 미요청 기능 구현, 임의 테스트 삭제 시 개발자가 개입

## 2. 개발 방법론 - TDD (Red → Green → Refactor)

모든 코드는 테스트 주도 개발 방식으로 작성합니다.

### 2.1 Red Phase
- 요구사항을 만족하는 실패 테스트 케이스 먼저 작성
- 3A 원칙 준수 (Arrange - Act - Assert)

### 2.2 Green Phase
- 테스트를 통과하는 최소한의 코드 작성
- 오버엔지니어링 금지

### 2.3 Refactor Phase
- 코드 품질 개선 및 불필요한 코드 제거
- 객체지향 원칙 준수
- 모든 테스트 통과 필수

## 3. 코드 품질 기준

### 3.1 금지 사항 (Never Do)
- Mock 남발: 실제 동작하지 않는 코드, 과도한 Mock 사용 금지
- null-safety 위반: Optional 활용 필수 (Java)
- 디버깅 코드: println, System.out 등 잔류 금지

### 3.2 권장 사항 (Recommendation)
- E2E 테스트로 실제 API 동작 검증
- 재사용 가능한 객체 설계
- 성능 최적화 대안 제시
- 완성된 API는 `http/**/*.http`에 문서화

### 3.3 우선순위 (Priority)
1. 실제 동작하는 해결책만 고려
2. null-safety, thread-safety 보장
3. 테스트 가능한 구조 설계
4. 기존 코드 패턴과 일관성 유지

## 4. 오류 수정 컨벤션

오류를 수정할 때는 반드시 아래 형식으로 **오류 수정 이력** 섹션에 기록한다.

| 항목 | 설명 |
|------|------|
| **AS-IS** | 수정 전 코드 |
| **TO-BE** | 수정 후 코드 |
| **왜 (Why)** | 왜 이 수정이 필요한지 |
| **동작 원리** | 내부적으로 어떻게 동작하는지, 이유 |
| **검증 테스트** | 수정이 올바른지 확인하는 테스트 |

## 5. Git 컨벤션

- **커밋 주체**: 개발자진행 방향가 직접 수행 (AI 임의 커밋 금지)
- **커밋 메시지**: Conventional Commits 형식 권장

## 6. AI 협업 스타일

본 프로젝트에서 AI와의 협업은 다음 방식을 지향합니다:

| 스타일 | 설명 |
|--------|------|
| **Planning-first** | 개발자가 먼저 설계하고, AI로 검증 및 대안 비교 |
| **Explanation-seeking** | 코드의 이유, 원리, 동작에 대한 설명 요구 |
| **Iterative-reasoning** | 문제를 분해하여 추론 → 질문 → 수정 반복 |

> AI는 답을 제공하는 것이 아닌, 사고를 돕는 도구로 활용합니다.

---

## 오류 수정 이력

오류 수정 시 AS-IS / TO-BE / 왜(Why) / 동작 원리를 반드시 기록한다.

---

### [#1] CoreException 예외 원인(cause) 유실 문제

**출처**: CodeRabbit 리뷰

#### AS-IS

```java
// CoreException — cause를 받는 생성자 없음
public CoreException(ErrorType errorType, String customMessage) {
    super(customMessage != null ? customMessage : errorType.getMessage());
    this.errorType = errorType;
    this.customMessage = customMessage;
}

// BirthDate.parseDate — 원래 예외(e)를 전달하지 않음
catch (DateTimeParseException e) {
    throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
            "생년월일은 YYYY-MM-DD 형식이어야 합니다.");
}
```

#### TO-BE

```java
// CoreException — cause를 받는 3-파라미터 생성자 추가
public CoreException(ErrorType errorType, String customMessage, Throwable cause) {
    super(customMessage != null ? customMessage : errorType.getMessage(), cause);
    this.errorType = errorType;
    this.customMessage = customMessage;
}

// BirthDate.parseDate — 원래 예외(e)를 cause로 전달
catch (DateTimeParseException e) {
    throw new CoreException(UserErrorType.INVALID_BIRTH_DATE,
            "생년월일은 YYYY-MM-DD 형식이어야 합니다.", e);
}
```

#### 왜 (Why)

`catch`에서 새로운 예외를 던질 때 원래 예외(`e`)를 넘기지 않으면 **예외 체인(Exception Chain)이 끊긴다.**
운영 환경에서 장애가 발생했을 때 로그에 `Caused by`가 남지 않아, 정확히 어떤 입력값이 왜 실패했는지 추적할 수 없다.

#### 동작 원리

Java의 모든 예외는 `Throwable.cause` 필드를 가진다. `super(message, cause)`로 원인을 연결하면 예외 체인이 형성된다.

```
// cause 없는 경우 — 원인 추적 불가
CoreException: 생년월일은 YYYY-MM-DD 형식이어야 합니다.
    at BirthDate.parseDate(BirthDate.java:52)

// cause 있는 경우 — 원인 추적 가능
CoreException: 생년월일은 YYYY-MM-DD 형식이어야 합니다.
    at BirthDate.parseDate(BirthDate.java:52)
Caused by: DateTimeParseException: Text '1994/11/15' could not be parsed
    at java.time.format.DateTimeFormatter.parseResolved0(...)
```

`Caused by`가 있어야 "어떤 값이, 어떤 이유로 파싱에 실패했는지" 정확히 파악할 수 있다. 이는 운영 환경에서 장애 원인 파악 시간을 줄이는 데 직결된다.

#### 검증 테스트

```java
@DisplayName("잘못된 형식이면, 예외의 원인으로 DateTimeParseException을 포함한다.")
@Test
void preservesCauseWhenInvalidFormat() {
    CoreException exception = assertThrows(CoreException.class, () -> {
        new BirthDate("1994/11/15");
    });
    assertThat(exception.getCause()).isInstanceOf(DateTimeParseException.class);
}
```