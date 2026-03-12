---
name: analyze-tx-rollback
description: >
  Spring @Transactional 코드에서 "rollback-only" 마킹으로 인한 UnexpectedRollbackException
  발생 가능 지점을 찾아내고, 직접 오류를 체험하며 원인을 코드 레벨까지 파고드는 학습을 돕는다.

  특히 다음 패턴을 중점적으로 탐지한다.
  - 외부 @Transactional 메서드가 내부 @Transactional 메서드를 호출하면서 RuntimeException을 catch하는 구조
  - PROPAGATION_REQUIRED(기본값)로 합류한 트랜잭션 안에서 예외가 흡수되는 구조
  - try/catch로 예외를 삼켰지만 rollback-only 플래그가 이미 찍히는 구조

  단순히 문제를 알려주는 게 아니라, 직접 예외를 재현하게 유도하고
  로그를 해석하며 원인을 스스로 찾아낼 수 있도록 돕는다.

  다음 키워드가 포함된 요청에는 반드시 이 스킬을 사용한다:
  rollback-only, UnexpectedRollbackException, TransactionSystemException,
  트랜잭션 롤백, 트랜잭션 안에서 catch, 내부 트랜잭션, 전파속성, PROPAGATION_REQUIRED,
  globalRollbackOnParticipationFailure, 트랜잭션 왜 롤백, 예외 잡았는데 롤백
---

## 📌 이 스킬의 학습 목표

> "@Transactional 안에서 try/catch로 예외를 잡았는데 왜 롤백되지?"
> 라는 질문에 코드 레벨까지 스스로 답할 수 있게 되는 것.

---

## 🔍 Step 1. 코드에서 위험 패턴 탐지

코드를 보면 아래 패턴을 찾는다.

### ⚠️ 위험 패턴 체크리스트

```
□ 외부 메서드에 @Transactional이 있는가?
□ 그 외부 메서드가 내부 @Transactional 메서드를 호출하는가?
□ 내부 메서드가 RuntimeException을 던질 수 있는가?
□ 외부 메서드에서 그 예외를 try/catch로 잡고 있는가?
□ 내부 메서드의 전파속성(propagation)이 기본값(REQUIRED)인가?
```

모두 해당하면 → **UnexpectedRollbackException 발생 가능 지점**

### 탐지 예시

```java
// ✅ 위험: 아래 구조는 rollback-only를 유발한다
@Service
@Transactional  // 외부 트랜잭션 시작
public class OuterService {

    public void outerMethod() {
        try {
            innerService.innerMethod();  // 내부 @Transactional 메서드 호출
        } catch (RuntimeException e) {
            log.warn("잡았다!");  // ← 이미 늦음. 트랜잭션엔 rollback-only 찍혔음
        }
    }
}

@Service
@Transactional  // 기본 propagation = REQUIRED → 외부 트랜잭션에 합류
public class InnerService {

    public void innerMethod() {
        repository.save(...);
        throw new RuntimeException("내부 오류");  // ← 여기서 rollback-only 마킹됨
    }
}
```

---

## 🧪 Step 2. 직접 재현하기 (체험 학습)

탐지된 패턴이 있으면, 아래 순서대로 직접 오류를 겪어보게 유도한다.

### 2-1. 최소 재현 코드 작성

프로젝트 구조에 맞게 아래 구조를 만든다.

```java
// 1. 내부 서비스 - RuntimeException을 던지는 @Transactional 메서드
@Service
@Transactional
public class InnerService {
    private final SomeRepository repository;

    public void methodThatThrows() {
        repository.save(new SomeEntity("테스트 데이터"));
        throw new RuntimeException("의도적 RuntimeException");
    }
}

// 2. 외부 서비스 - catch로 예외를 삼키는 @Transactional 메서드
@Service
@Transactional
public class OuterService {
    private final InnerService innerService;

    public void outerMethod() {
        try {
            innerService.methodThatThrows();
        } catch (RuntimeException e) {
            log.warn("예외 잡음: {}", e.getMessage());
            // "여기서 잡았으니 커밋되겠지?" ← 이게 함정
        }
    }
}

// 3. 테스트 or Runner로 실행
@Test
void 롤백_직접_겪어보기() {
    assertThatThrownBy(() -> outerService.outerMethod())
        .isInstanceOf(UnexpectedRollbackException.class);
}
```

### 2-2. 로그 설정 추가 (application.yml)

```yaml
logging:
  level:
    org.springframework.transaction: TRACE
    org.springframework.orm.jpa: DEBUG
```

이 설정을 켜야 아래 핵심 로그가 보인다.

---

## 🔬 Step 3. 로그 해석 훈련

실행 후 아래 로그가 순서대로 나오는지 확인한다.

```
# 1. 외부 트랜잭션 생성
Creating new transaction with name [OuterService.outerMethod]

# 2. 내부 메서드가 기존 트랜잭션에 합류
Participating in existing transaction

# 3. ⚠️ 핵심 - RuntimeException 발생 후 rollback-only 마킹
Participating transaction failed - marking existing transaction as rollback-only

# 4. 외부에서 예외를 catch함 (하지만 이미 늦음)
[warn 로그: "예외 잡음"]

# 5. 외부 메서드 종료 후 커밋 시도
Initiating transaction commit

# 6. ❌ 커밋하려는 순간 강제 롤백
Transaction silently rolled back because it has been marked as rollback-only
```

### 로그 해석 포인트

| 로그 | 의미 |
|------|------|
| `Participating in existing transaction` | 내부 메서드가 외부 트랜잭션에 합류함 (한 몸이 됨) |
| `marking existing transaction as rollback-only` | 내부에서 예외 발생 → 트랜잭션에 "망함" 플래그 찍힘 |
| `silently rolled back` | 커밋 시도했지만 플래그 때문에 롤백 강행됨 |

---

## 🧠 Step 4. 원인 코드 레벨 파고들기 (DFS)

### Q1. 왜 catch해도 소용없나?

```
catch로 예외를 흡수하는 건 "Java 예외 레벨" 처리.
그런데 rollback-only 마킹은 "트랜잭션 레벨"에서 이미 발생함.
→ 레이어가 다르다. catch는 트랜잭션 상태를 되돌리지 못한다.
```

### Q2. rollback-only 마킹은 어디서 일어나나?

`AbstractPlatformTransactionManager.java` 안에 있다.

```java
// Participating in larger transaction
if (status.hasTransaction()) {
    if (status.isLocalRollbackOnly() || isGlobalRollbackOnParticipationFailure) {
        // ← isGlobalRollbackOnParticipationFailure 기본값이 true
        doSetRollbackOnly(status);  // ← 여기서 플래그 찍힘
    }
}
```

### Q3. 왜 기본값이 true인가?

Spring의 철학:
> "참여 중인 트랜잭션 일부가 실패하면, 전체를 롤백하는 게 안전하다."
> 데이터 정합성을 위해 부분 커밋보다 전체 롤백을 기본으로 선택함.

### Q4. 꼬리 질문 - 그러면 어떻게 해결하나?

아래 3가지 선택지가 있다.

| 해결 방법 | 핵심 아이디어 | 주의사항 |
|-----------|--------------|----------|
| `PROPAGATION_REQUIRES_NEW` | 내부를 완전히 별도 트랜잭션으로 분리 | 내부 커밋은 외부 롤백과 무관해짐 → 정합성 설계 필요 |
| `PROPAGATION_NESTED` | 서브트랜잭션 실패 시 savepoint까지만 롤백 | **JPA에서 동작 안 함** (JpaDialect savepoint 미지원) |
| 구조 재설계 | 예외를 트랜잭션 안에서 잡으려는 시도 자체를 제거 | 가장 근본적인 해결 |

### Q5. 가장 중요한 꼬리 질문

> **"트랜잭션 안에서 RuntimeException을 왜 잡으려고 했나요?"**

이 질문에 답하지 못하면 기술적 해결보다 설계 재검토가 먼저다.
예외 처리 로직이 비즈니스 흐름에 끼어드는 구조가 문제의 시작인 경우가 많다.

---

## ✅ Step 5. 해결 후 검증

### REQUIRES_NEW로 분리하는 경우

```java
@Service
public class InnerService {

    @Transactional(propagation = Propagation.REQUIRES_NEW)  // 별도 트랜잭션
    public void methodThatThrows() {
        repository.save(...);
        throw new RuntimeException("내부 오류");
        // 내부 트랜잭션만 롤백됨. 외부 트랜잭션은 영향 없음.
    }
}
```

검증 포인트:
```
□ 외부 메서드가 UnexpectedRollbackException 없이 정상 종료되는가?
□ 내부에서 저장한 데이터는 롤백되었는가?
□ 외부에서 저장한 데이터는 커밋되었는가?
□ 이 동작이 비즈니스 요구사항에 맞는가?
```

---

## 📝 학습 정리 템플릿

체험 후 아래 형식으로 정리하면 블로그 포스팅으로 이어질 수 있다.

```markdown
## 문제 상황
- 어떤 코드에서, 어떤 동작을 기대했는가?

## 실제 동작
- 어떤 예외가 발생했는가?
- 로그에서 어떤 순서로 무슨 일이 일어났는가?

## 원인
- 핵심 원인은 무엇인가? (코드 레벨로)
- Spring의 어떤 설계 철학에서 비롯된 것인가?

## 해결 방법과 Trade-off
- 선택한 해결 방법은?
- 그 방법의 한계 또는 주의사항은?

## 배운 것
- 기술적으로 새로 알게 된 것
- 설계 관점에서 바뀐 생각
```

결과는 /Users/jins/Desktop/2026 Project/loop-pack-be-l2-vol3-java/.claude/skills/analyzing-tx-rollback에 .md로 정리
