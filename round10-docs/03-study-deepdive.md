# Round 10 Deep Dive - 깊게 들어간 주제들

> 학습 중 사용자가 많이 헷갈려하거나 깊게 탐구한 주제를 별도 저장
> - 사용자의 질문/판단/의견은 최대한 원문 그대로 보존
> - 결론/근거는 기술적 오류 없이 fact만 기술

---

## 1. 이메일 발송 작업의 배치 처리 멱등성 문제

### 사용자 질문/의견
> "근본적 한계라 안된다는 핑계를 누가 들어줘요? 아무도 용납 안할거같아요."
> "애초에 이런 이메일 발송 작업은 배치처리를 하면 안되는건가요?"

### 결론

이메일 발송 + DB 상태 업데이트를 같은 트랜잭션 범위에 묶으려 할 때 발생하는 문제다. 해결책은 **Outbox Pattern**으로 두 관심사를 분리하는 것이다.

**Outbox Pattern 흐름:**
1. Batch Writer (트랜잭션 내): `is_notified = true` 업데이트 + `email_queue` 테이블에 INSERT → 커밋
2. 별도 이메일 워커 (독립): `email_queue`에서 읽어 실제 이메일 발송 → 발송 완료 후 queue에서 삭제

**핵심**: 외부 시스템(이메일)과의 통신을 DB 쓰기로 변환해 트랜잭션 보장. 이메일 발송 실패 시 queue에 데이터가 남아 워커가 재시도하며, 이미 발송된 건은 queue에서 삭제되어 중복 없음.

---

## 2. JdbcBatchItemWriter의 벌크 처리 내부 동작

### 사용자 질문
> "벌크 update를 할때 jpa에서 지원이 안되는걸로 알고있는데.. batch 자체에서 bulk 쓰기/읽기를 제공해주는건지, 아니면 우리가 따로 구현을 해야하는지?"

### 결론

Spring Batch는 `JdbcBatchItemWriter`를 제공하며, 내부적으로 JDBC의 `addBatch()` / `executeBatch()`를 사용한다.

- 1000건 → `pstmt.addBatch()` 1000번 (SQL 미실행, 쌓기만)
- `pstmt.executeBatch()` 1번 → DB로 한 번에 전송 (네트워크 왕복 1회)
- `JpaItemWriter`는 내부적으로 `merge()`를 1건씩 호출 → 진짜 벌크가 필요하면 `JdbcBatchItemWriter`가 적합
