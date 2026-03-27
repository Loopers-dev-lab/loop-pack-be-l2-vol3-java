# Week 7: ApplicationEvent → Kafka 이관 로드맵

## 전체 흐름

```
Step 1. ApplicationEvent (현재 진행중)
Step 2. 이벤트 기준 정립 (LikedEvent, UnlikedEvent 등)
Step 3. Kafka Producer/Consumer로 교체
```

---

## Step 1. ApplicationEvent 설계 원칙

### 왜 @EventListener가 아닌 @TransactionalEventListener(AFTER_COMMIT)인가?

**@EventListener** (같은 트랜잭션 내 실행)
```
like() 트랜잭션 시작
  → Like 저장
  → 이벤트 발행 → [즉시 실행] product.increaseLikeCount()
  → 커밋 (Like + likesCount 동시에)
```
- 기존 코드와 동작이 동일. 분리 효과가 없음.
- Kafka 이관 시 다시 설계해야 함.

**@TransactionalEventListener(AFTER_COMMIT)** (커밋 후 실행)
```
like() 트랜잭션 시작
  → Like 저장
  → 이벤트 발행 (대기열 등록만)
  → 커밋 ← Like만 먼저 DB에 반영
→ [AFTER_COMMIT] product.increaseLikeCount() ← 별도 트랜잭션
```
- Like 저장과 likesCount 업데이트가 분리됨 (eventual consistency)
- Kafka도 동일한 구조 (커밋 후 메시지 발행 → Consumer 처리)
- **Kafka로 교체할 때 리스너만 바꾸면 됨**

### 핵심 포인트: REQUIRES_NEW가 필요한 이유

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)  // ← 필수
public void handle(LikedEvent event) { ... }
```

AFTER_COMMIT 시점에는 원래 트랜잭션이 이미 끝났다.
이 상태에서 `@Transactional`만 붙이면 트랜잭션 없이 실행되므로,
dirty checking이 동작하지 않아 `product.increaseLikeCount()`가 DB에 반영되지 않는다.
`REQUIRES_NEW`로 새 트랜잭션을 명시적으로 열어야 한다.

---

## Step 2. 이벤트 기준 정립

### 완료된 이벤트

| 이벤트 | 파일 | 발행 위치 | 처리 위치 |
|--------|------|-----------|-----------|
| `LikedEvent` | `domain/like/LikedEvent.java` | `LikeService.like()` | `LikedEventListener` |

### 남은 이벤트 (TODO)

| 이벤트 | 발행 위치 | 처리 내용 |
|--------|-----------|-----------|
| `UnlikedEvent` | `LikeService.unlike()` | `product.decreaseLikeCount()` + 캐시 evict |

### 이벤트 설계 기준

이벤트를 두 개로 분리 (LikedEvent / UnlikedEvent) vs 하나로 통합 (타입 구분)?

→ **두 개로 분리**를 선택.
- 소비자(리스너/Kafka Consumer)가 타입을 분기하지 않아도 됨
- 각 이벤트가 명확한 의미를 가짐
- Kafka 토픽 설계 시에도 이벤트별 토픽 분리가 자연스러움

---

## Step 3. Kafka 이관 계획 (예정)

ApplicationEvent 단계와 Kafka 단계의 구조 비교:

| | ApplicationEvent | Kafka |
|---|---|---|
| 발행 | `eventPublisher.publishEvent(new LikedEvent(...))` | `kafkaTemplate.send("liked-event", ...)` |
| 구독 | `@TransactionalEventListener` | `@KafkaListener` |
| 트랜잭션 | AFTER_COMMIT + REQUIRES_NEW | 별도 Consumer 트랜잭션 |
| 실패 처리 | 예외 전파 | DLT(Dead Letter Topic) |

**이관 시 변경 범위:**
- `LikeService`: `publishEvent` → `kafkaTemplate.send`
- `LikedEventListener` → `LikedEventKafkaConsumer`로 교체
- `LikedEvent`: Kafka 직렬화를 위한 `@JsonSerialize` 등 추가 가능

---

## 현재 파일 구조

```
domain/like/
  LikedEvent.java          ← record(memberId, productId)

application/like/
  LikeService.java         ← Like 저장 + 이벤트 발행
  LikedEventListener.java  ← AFTER_COMMIT에서 likesCount 업데이트 + 캐시 evict
```

## 의사결정 기록

- `likesCount`는 eventual consistency 허용 → 좋아요 수가 1~2초 늦게 반영돼도 UX에 무관
- `LikedEvent`에 `memberId`도 포함 → Kafka 이관 후 "누가 좋아요를 눌렀는지" 맥락 활용 가능
- 상품 존재 검증: `findById()` 대신 `existsById()` → 리스너에서 별도로 로드하므로 중복 조회 방지
