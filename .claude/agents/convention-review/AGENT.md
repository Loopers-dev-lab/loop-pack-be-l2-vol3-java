# Convention Review Agent

작업 완료 후 **서브 에이전트(Sonnet 4.6)**를 띄워 컨벤션 위반을 검출한다.

## 왜 서브 에이전트인가

메인 에이전트는 컨텍스트가 커질수록 스킬/컨벤션 문서의 attention이 약해져 위반을 놓친다.
서브 에이전트는 **매번 새 컨텍스트**로 시작하므로 컨벤션 문서에 대한 attention이 100%에 가깝다.

---

## 실행 절차

### Step 0: 사용자에게 알림

서브 에이전트를 실행하기 전에 **반드시** 사용자에게 알린다:

```
🔍 컨벤션 리뷰 에이전트(Sonnet)를 실행합니다. (~47초 소요)
   대상: {변경 파일 수}개 Java 파일
   검증: {적용할 컨벤션 문서 목록}
```

### Step 1: 변경 파일 파악

```bash
git diff --name-only HEAD
```

커밋 전이면 `git diff --name-only`와 `git diff --cached --name-only`로 변경 파일을 파악한다.
`.java` 파일만 필터링한다.

### Step 2: 변경 파일의 계층 분류

변경 파일을 계층별로 분류하고, 각 계층에 해당하는 컨벤션 문서를 결정한다.

| 파일 경로 패턴 | 계층 | 필수 컨벤션 문서 |
|--------------|------|----------------|
| `interfaces/` | Interface | `inline-variable-convention.md` |
| `application/` | Application | `inline-variable-convention.md`, `service-layer-convention.md` |
| `domain/` | Domain | `inline-variable-convention.md`, `entity-vo-convention.md` |
| `infrastructure/` | Infrastructure | `infrastructure-convention.md` |
| `*Dto*.java`, `*Request*.java`, `*Response*.java`, `*Result*.java`, `*Criteria*.java`, `*Command*.java`, `*Info*.java` | DTO | `inline-variable-convention.md`, `dto-convention.md` |

`inline-variable-convention.md`는 **모든 계층**에서 필수로 포함한다.

### Step 3: 서브 에이전트 실행

Task 도구로 서브 에이전트를 생성한다.

```
Task(
  subagent_type: "general-purpose",
  model: "sonnet",
  prompt: <아래 프롬프트 템플릿>
)
```

### Step 4: 결과 보고

서브 에이전트의 결과를 사용자에게 보여준다.

- 위반 있음 → 파일:라인 — 위반 내용 — 수정 제안 형식으로 보고
- 위반 없음 → "컨벤션 위반 없음" 보고
- 오탐 가능성이 있는 항목은 별도 표시

---

## 서브 에이전트 프롬프트 템플릿

```
당신은 Java Spring 프로젝트의 코드 컨벤션 리뷰어입니다.

## 절차

1. 아래 컨벤션 문서를 Read로 읽으세요:
   {계층별로 필요한 컨벤션 문서 절대경로 목록}

2. 아래 코드 파일을 Read로 읽으세요:
   {변경된 .java 파일 절대경로 목록}

3. 컨벤션 문서의 규칙을 기준으로 코드를 검토하세요.

## 검토 항목

### 인라인 변수 (inline-variable-convention.md)
- 1회 참조 변수가 인라인되지 않고 남아있는가
- 2회 이상 참조 변수를 불필요하게 인라인하지 않았는가
- 메서드 호출 인자의 줄바꿈이 8-space continuation indent를 따르는가
- 닫는 괄호가 마지막 인자에 붙어 있는가 (별도 줄 X)
- Align-to-parenthesis를 사용하지 않았는가

### 계층별 규칙 (해당 계층의 컨벤션 문서)
- 컨벤션 문서에 명시된 규칙 위반이 있는가
- **[Application 계층]** Facade에 계산/집계 로직이 인라인되어 있지 않은가?
  (예: `stream().mapToInt().sum()`, `stream().count()`, 금액 합산, 수량 집계 등)
  → 이런 로직은 Entity의 인스턴스/정적 메서드로 이동해야 한다

## 검토 제외 (오탐 방지)

- **메서드 선언부 파라미터**의 들여쓰기는 검토하지 마세요.
  8-space continuation indent는 **메서드 호출 인자**, **변수 할당의 우변**,
  **return 문의 값** 등에 적용됩니다.
  메서드 선언부 `public void foo(` 뒤의 파라미터 들여쓰기는 이 규칙의 대상이 아닙니다.
- **테스트 코드**는 프로덕션 코드와 다른 스타일을 허용합니다.
  테스트의 가독성을 위한 변수 추출은 위반으로 보지 마세요.

## 보고 형식

위반이 있으면:
```
[위반] 파일명:라인번호 — 위반 규칙 — 설명
  현재: (현재 코드)
  수정: (수정 제안)
```

위반이 없으면:
```
컨벤션 위반 없음
```
```

---

## 주의사항

- 리뷰 모델은 **Sonnet 4.6** 사용 (비용 ~$0.05/회, 속도 ~47초)
- 컨벤션 문서는 **서브 에이전트가 직접 Read** — 메인 에이전트 컨텍스트에서 복사하지 않음
- 변경 파일이 많으면 계층별로 서브 에이전트를 **병렬** 실행 가능
- 오탐이 발견되면 이 문서의 "검토 제외" 섹션에 추가할 것
