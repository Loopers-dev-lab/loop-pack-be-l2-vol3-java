# Outbox 아키텍처 의사결정 기록

> Phase 2 구현 과정에서 내린 설계 결정과 그 배경, 트레이드오프를 정리한다.
> PR 설명이나 블로그 글 작성 시 참고 자료로 활용한다.

---

## 결정 1. Outbox의 레이어 배치 — 도메인 vs 인프라

### 선택지

| | Domain 레이어 (DIP 적용) | Infrastructure 레이어 (DIP 미적용) |
|---|---|---|
| **패키지 구조** | `domain/outbox/OutboxEvent`, `OutboxEventRepository`(인터페이스) → `infrastructure/outbox/OutboxEventRepositoryImpl` | `infrastructure/outbox/` 에 엔티티, 리포지토리, 리스너 모두 배치 |
| **의존성 방향** | domain ← infrastructure (DIP 준수) | infrastructure 내부에서 자체 완결 |
| **도메인 순수성** | Outbox 개념이 도메인에 노출됨 | 도메인 레이어에 Outbox 관련 코드 전무 |
| **테스트 대역** | 인터페이스 기반 mock 가능 | JPA Repository 직접 의존 |
| **코드량** | Repository 인터페이스 + 구현체 + Service 필요 | 엔티티 + JPA Repository만 |

### 최종 결정: **Infrastructure 레이어 (DIP 미적용)**

Outbox는 "이벤트를 안전하게 전달하기 위한 기술적 안전장치"이지, 비즈니스 규칙이 아니다.
도메인 레이어가 Outbox의 존재를 알 필요가 없다.

```
infrastructure/outbox/
├── OutboxEvent.java              # JPA 엔티티
├── OutboxEventJpaRepository.java # Spring Data JPA
├── OutboxRecorder.java           # 직렬화 + 저장
├── OutboxEventRecordListener.java# BEFORE_COMMIT 리스너
├── OutboxRelayScheduler.java     # 폴링 → Kafka 발행
├── OutboxMessage.java            # Kafka 메시지 엔벨로프
└── OutboxTopics.java             # 토픽명 상수
```

### 트레이드오프

- **얻은 것**: 도메인 레이어 순수성 유지. Outbox 관련 코드가 한 패키지에 응집되어 파악이 쉬움. 불필요한 추상화 계층(Repository 인터페이스, Service) 제거로 코드 단순화.
- **잃은 것**: DIP 미적용으로, Outbox 구현체를 교체하려면 infrastructure 패키지 내부를 직접 수정해야 함. 단, Outbox 구현체를 교체할 현실적 시나리오가 없으므로 이 단점은 YAGNI 관점에서 수용.

### 판단 근거

> "Outbox는 도메인 이벤트가 Kafka까지 '안전하게 도달하는 경로'를 보장하는 장치다.
> 비즈니스 룰을 캡슐화하지 않고, 도메인 객체 간 관계를 표현하지 않으며,
> Kafka가 아닌 다른 메시지 브로커로 바뀌더라도 도메인 로직에는 변화가 없다.
> 따라서 infrastructure에 두는 것이 자연스럽다."

---

## 결정 2. Outbox 기록 방식 — Facade 직접 호출 vs BEFORE_COMMIT 리스너

### 선택지

| | A. Facade에서 OutboxRecorder 직접 호출 | B. BEFORE_COMMIT 리스너에서 기록 |
|---|---|---|
| **호출 구조** | `Facade → OutboxRecorder.record()` | `Facade → eventPublisher.publish()` → Spring이 `OutboxEventRecordListener` 호출 |
| **Facade 책임** | Outbox 기록이라는 인프라 관심사를 직접 알아야 함 | 도메인 이벤트만 발행, Outbox 존재를 모름 |
| **트랜잭션** | 같은 TX (Facade의 @Transactional 내부) | 같은 TX (BEFORE_COMMIT은 커밋 전 동기 실행) |
| **확장성** | 이벤트 추가 시 Facade에 Outbox 기록 코드도 추가 | 리스너에 핸들러 메서드만 추가 |
| **추적성** | Facade 코드에서 Outbox 기록이 명시적으로 보임 | 이벤트 흐름을 따라가야 Outbox 기록 지점을 찾을 수 있음 |

### 최종 결정: **B. BEFORE_COMMIT 리스너 (29CM 방식)**

```
[Facade]                     [Spring Event]              [Infrastructure]
OrderFacade.create()    →    OrderCreatedEvent     →     OutboxEventRecordListener
  - 도메인 로직 수행              (도메인 이벤트)                .onOrderCreated()
  - eventPublisher.publish()                                  → OutboxRecorder.record()
                                                              (같은 TX, 커밋 직전 실행)
```

### 트레이드오프

- **얻은 것**: Facade가 "무엇이 일어났는지"(이벤트 발행)만 표현하고, "그것을 어떻게 전달하는지"(Outbox 기록)를 모름. 관심사 분리가 깔끔. 새 이벤트 추가 시 Facade 코드 변경 없이 리스너 메서드만 추가하면 됨.
- **잃은 것**: 이벤트 발행 → Outbox 기록 흐름이 코드상 명시적이지 않음. `OutboxEventRecordListener`를 모르면 Outbox에 데이터가 언제 들어가는지 파악하기 어려움. 암묵적 흐름이므로 디버깅 시 이벤트 리스너 바인딩을 따라가야 함.

### 판단 근거

> 29CM 기술 블로그의 패턴을 참고했다.
> Facade는 유스케이스 조율자로서 "도메인 이벤트를 발행한다"는 의도만 표현하고,
> 그 이벤트를 Outbox에 기록하는 것은 인프라 리스너의 책임이다.
> BEFORE_COMMIT이므로 같은 TX에 참여하여 원자성이 보장되고,
> 도메인 코드에서 Outbox라는 인프라 개념이 완전히 사라진다.

### 참고: BEFORE_COMMIT의 트랜잭션 보장

```
@Transactional
OrderFacade.create() {
    orderService.create(...)          // ← 도메인 데이터 INSERT
    eventPublisher.publish(event)     // ← 이벤트 발행 (아직 커밋 전)
}
// ── TX 커밋 시작 ──
// 1. BEFORE_COMMIT 리스너 실행 → outbox_events INSERT (같은 TX)
// 2. DB COMMIT (domain + outbox 원자적 커밋)
// 3. AFTER_COMMIT 리스너 실행 (별도 처리)
```

BEFORE_COMMIT 리스너에서 예외가 발생하면 TX 전체가 롤백되므로,
도메인 데이터만 저장되고 Outbox가 누락되는 상황은 원천 차단된다.

---

## 결정 3. 좋아요 카운트 — Spring Event vs Kafka, 이중 집계 문제

### 배경

좋아요 등록 시 두 가지 경로로 카운트가 증가하는 구조:

| 경로 | 메커니즘 | 대상 테이블 | 소비자 |
|------|---------|------------|--------|
| Spring Event (AFTER_COMMIT) | `Product.increaseLikeCount()` | `products.like_count` | **API 응답** (상품 목록/상세) |
| Outbox → Kafka → Consumer | `ProductMetrics.incrementLikeCount()` | `product_metrics.like_count` | **분석/통계** 시스템 |

### 왜 이중 집계가 아닌가

두 경로는 **서로 다른 소비자**를 위한 것이다:

- `products.like_count`: API 응답에 즉시 반영되어야 하는 **실시간 표시용 카운터**. 사용자가 좋아요를 누르고 새로고침하면 바로 반영되어야 한다.
- `product_metrics.like_count`: 분석 시스템이 참조하는 **이벤트 소싱 기반 집계**. 약간의 지연이 허용되지만, 유실 없는 정확성이 중요하다.

같은 "좋아요 +1"이지만 목적과 보장 수준이 다르므로 중복이 아니라 **의도적 공존**이다.

### 트레이드오프

- **얻은 것**: API 실시간성(Spring Event, 동기적 반영)과 이벤트 전달 안전성(Outbox, At Least Once)을 각각의 장점으로 활용.
- **잃은 것**: 두 카운터가 일시적으로 불일치할 수 있음. Spring Event는 AFTER_COMMIT + @Async로 동작하므로 앱 크래시 시 유실 가능(At Most Once). Kafka 경로는 Outbox로 보장되지만 Relay 주기(1초)만큼 지연.

### 불일치 보정 전략 (향후)

Spring Event 유실로 인한 `products.like_count` 드리프트는 **보정 배치(Reconciliation Batch)** 로 해결 예정:
- 주기적으로 `product_metrics` (정확한 집계) 값을 `products.like_count`에 동기화
- Phase 2 범위 밖이므로 개념만 인지하고 후속 구현 예정

---

## 결정 4. Outbox 테이블 인덱스 설계

### 선택지

| | 인덱스 없음 | `(published_at, created_at)` 복합 인덱스 |
|---|---|---|
| **Relay 조회 성능** | 매 폴링마다 Full Table Scan | 인덱스 레인지 스캔 |
| **저장 비용** | 없음 | B-Tree 인덱스 추가 유지 비용 |
| **운영 리스크** | 데이터 누적 시 조회 성능 급격히 저하 | 미발행 건수 비례로만 비용 증가 |

### 최종 결정: **`(published_at, created_at)` 복합 인덱스**

```java
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_unpublished", columnList = "published_at, created_at")
})
```

### 동작 원리

Relay 스케줄러의 쿼리:
```sql
SELECT * FROM outbox_events
WHERE published_at IS NULL
ORDER BY created_at ASC
LIMIT 100
```

MySQL InnoDB의 B-Tree에서 `NULL` 값은 가장 앞에 정렬된다.
따라서 `published_at IS NULL` 조건은 인덱스의 **선두 영역만 스캔**하면 되고,
두 번째 컬럼 `created_at`으로 정렬 순서까지 인덱스가 보장한다.

발행 완료된 레코드(`published_at IS NOT NULL`)는 인덱스 뒤쪽으로 밀려나므로,
테이블에 수백만 건이 쌓여도 미발행 건만 빠르게 조회할 수 있다.

---

## 결정 5. Outbox Relay 발행 안전성 — 비동기 fire-and-forget vs 동기 ACK 확인

### 문제

초기 구현에서 `kafkaTemplate.send().whenComplete(...)` 비동기 콜백만 등록하고, 콜백 결과와 무관하게 즉시 `markPublished()`를 호출했다.
브로커가 ACK을 보내지 않았거나 전송이 실패해도 published로 마킹되어 **메시지가 영구 유실**될 수 있다.

### 선택지

| | A. 비동기 fire-and-forget (기존) | B. 동기 `send().get(timeout)` + retryCount |
|---|---|---|
| **발행 확인** | 없음 (콜백 로그만) | 브로커 ACK 확인까지 블로킹 |
| **유실 가능성** | ACK 미수신 시 유실 | ACK 확인 후에만 markPublished — 유실 없음 |
| **실패 추적** | 로그만 | retryCount 누적, 한도 초과 시 markFailed → 수동 확인 대상 |
| **처리량** | 높음 (비동기) | 낮음 (동기 블로킹) |
| **트랜잭션** | 전체 배치 단일 TX (100건 × dirty checking) | 개별 이벤트 TX (FOR UPDATE → send → markPublished) |

### 최종 결정: **B. 동기 `send().get(timeout)` + retryCount**

```java
kafkaTemplate.send(topic, key, message)
        .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);  // 브로커 ACK까지 블로킹
locked.markPublished();  // ACK 확인 후에만 마킹
```

추가 안전장치:
- **retryCount + markFailed()**: 5회 실패 시 해당 이벤트를 `failed_at` 마킹하여 재시도 대상에서 제외. 수동 확인 필요.
- **FOR UPDATE 잠금**: 스케줄러 인스턴스가 2개 이상일 때 같은 이벤트 동시 발행 방지
- **연속 실패 차단**: 3건 연속 실패 시 사이클 조기 종료 — Kafka 장애 시 불필요한 반복 방지
- **개별 TX**: `TransactionTemplate`으로 이벤트마다 독립 트랜잭션 — 동기 블로킹으로 인한 장시간 TX 보유 방지

### 트레이드오프

- **얻은 것**: 메시지 유실 원천 차단. 실패 이벤트 추적 가능. 멀티 인스턴스 안전.
- **잃은 것**: 이벤트당 동기 블로킹(최대 5초) + 개별 TX로 DB 커넥션 사용 증가. 단, Relay는 1초 간격 폴링 + 최대 100건이므로 처리량 병목이 되지 않음.

---

## 결정 6. Consumer 실패 처리 — skip vs DLQ (Dead Letter Queue)

### 문제

배치 컨슈머에서 개별 메시지 처리가 실패했을 때, 단순 skip + 로그는 **메시지 영구 유실**을 의미한다.
분석/통계 데이터라 즉시 영향은 없지만, 누적되면 product_metrics 정합성이 깨진다.

### 선택지

| | A. skip + 로그 (기존) | B. DLQ 전송 | C. Spring ErrorHandler + DLQ | D. @RetryableTopic |
|---|---|---|---|---|
| **유실** | 영구 유실 | DLQ에 보관 (유실 없음) | DLQ에 보관 | retry 토픽 → DLT |
| **메인 흐름 영향** | 없음 | 없음 (실패 건만 DLQ) | 없음 | 없음 |
| **구현 복잡도** | 최소 | 낮음 (sendToDlq 메서드) | 중간 | 높음 |
| **배치 리스너 호환** | O | O | △ (배치 재처리 비효율) | X (단건 전용) |
| **후속 처리** | 없음 | 수동 확인/재처리 | 자동 + 수동 | 자동 재시도 |

### 최종 결정: **B. 배치 리스너 유지 + 실패 시 DLQ 직접 전송**

```java
try {
    for (OutboxMessage message : messages) {
        try {
            handler.handle(message);
        } catch (Exception e) {
            sendToDlq(message);  // 실패 건 → DLQ 토픽으로
        }
    }
    acknowledgment.acknowledge();
} catch (Exception e) {
    // DLQ 전송 자체가 실패 → ACK 안 함 → Kafka가 전체 배치 재배달
}
```

### 핵심 설계: 이중 try-catch + DLQ 전송 동기 블로킹

1. **안쪽 try-catch**: 개별 메시지 실패 → DLQ 전송 (`send().get(5s)` 동기 블로킹)
2. **바깥 try-catch**: DLQ 전송 실패 → ACK 안 함 → 전체 배치 재배달
3. **재배달 시 안전**: Handler의 멱등성 체크(`eventHandledRepository.existsByEventId`)로 이미 처리된 메시지 skip

### DLQ 후속 처리

현재는 **로그 + 모니터링 알림**까지만 구현. 자동 재처리는 미구현.
실무에서도 DLQ 메시지는 원인 파악 후 수동 처리가 일반적이다.
향후 필요 시 DLQ 컨슈머 또는 운영 어드민에서 원래 토픽으로 재발행하는 방식으로 확장 가능.

### 트레이드오프

- **얻은 것**: 메시지 유실 방지. 실패 메시지 보관으로 사후 분석/재처리 가능. 배치 리스너 구조 유지.
- **잃은 것**: DLQ 토픽 관리 필요. DLQ에 쌓인 메시지를 모니터링하지 않으면 의미 없음. DLQ 전송 실패 시 전체 재배달로 이미 성공한 메시지도 재처리됨 (멱등성으로 커버).

---

## 결정 7. Outbox/event_handled 테이블 데이터 증가 — 보존 vs 삭제

### 문제

`outbox_events`와 `event_handled` 테이블은 이벤트 발생마다 레코드가 추가된다.
삭제 정책이 없으면 무한히 증가하여 디스크와 인덱스 성능에 영향을 줄 수 있다.

### 선택지

| | A. 삭제 없음 (현재) | B. 보존 기간 기반 삭제 (Batch) | C. 발행 완료 즉시 삭제 |
|---|---|---|---|
| **디스크** | 무한 증가 | 보존 기간만큼만 유지 | 최소 (미발행 건만) |
| **감사/추적** | 전체 이력 보존 | 보존 기간 내 이력만 | 불가 (발행 즉시 삭제) |
| **구현 복잡도** | 없음 | 스케줄러/배치 필요 | Relay에 DELETE 추가 |
| **인덱스 성능** | 저하 가능 | 안정적 | 최상 |

### 현재 결정: **A. 삭제 없음 (학습 단계, 향후 고려)**

학습 목적이므로 데이터 삭제 정책은 구현하지 않는다.
실무에서는 메시지 규모, 감사 요구사항, 스토리지 비용에 따라 결정해야 한다.

### 실무 적용 시 고려 사항

- **outbox_events**: 발행 완료(`published_at IS NOT NULL`) + 7일 경과 건 삭제가 일반적. 실패 건(`failed_at IS NOT NULL`)은 수동 확인 후 별도 삭제.
- **event_handled**: 멱등성 보장 기간과 연관. Kafka 메시지 retention(기본 7일)보다 길게 유지해야 재배달된 오래된 메시지도 중복 차단 가능.
- 삭제 방식: `@Scheduled` + `DELETE WHERE published_at < NOW() - INTERVAL 7 DAY LIMIT 1000` (배치 삭제로 잠금 영향 최소화)

---

## 결정 8. ProductMetrics 동시성 전략 — JPA dirty checking + @Version vs atomic upsert

### 문제

`product_metrics` 테이블은 Kafka Consumer가 이벤트를 소비할 때마다 카운터를 증감한다.
같은 상품에 대한 이벤트가 동시에 도착하면 Lost Update가 발생할 수 있다.

### 선택지

| | A. JPA dirty checking + `@Version` | B. atomic upsert (`INSERT ... ON DUPLICATE KEY UPDATE`) |
|---|---|---|
| **UPDATE 방식** | `SET like_count = 6` (절대값 덮어쓰기) | `SET like_count = like_count + 1` (상대값 증감) |
| **동시성 안전** | `@Version`으로 충돌 감지 → 재시도 필요 | SQL 레벨에서 원자적 보장 → 재시도 불필요 |
| **행 미존재 시** | `getOrCreate` 패턴 (SELECT → INSERT/UPDATE 2번) | `INSERT ... ON DUPLICATE KEY UPDATE` (1문장 upsert) |
| **재시도 로직** | 필수 (OptimisticLockingFailureException 핸들링) | 불필요 |
| **도메인 모델** | 엔티티 메서드로 비즈니스 로직 표현 (`incrementLikeCount()`) | 쿼리에 로직이 들어감 (네이티브 SQL) |
| **JPA 지원** | 완전 지원 (dirty checking + @Version) | 미지원 — `@Query(nativeQuery = true)` 직접 작성 필수 |
| **복잡도** | 높음 (재시도 + TransactionTemplate 또는 별도 Handler) | 낮음 (단순 위임) |

### 최종 결정: **B. atomic upsert**

```java
// ProductMetricsJpaRepository — 네이티브 쿼리
@Modifying
@Query(value = "INSERT INTO product_metrics (product_id, like_count, order_count, view_count, updated_at) " +
        "VALUES (:productId, 1, 0, 0, NOW()) " +
        "ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW()",
        nativeQuery = true)
void incrementLikeCount(@Param("productId") Long productId);
```

감소 시 음수 방지:
```sql
ON DUPLICATE KEY UPDATE like_count = GREATEST(like_count - 1, 0), updated_at = NOW()
```

### 판단 근거

이 프로젝트에서 이미 `products.like_count`를 atomic UPDATE로 처리하고 있었다.
카운터성 필드의 동시성 문제에 atomic UPDATE가 가장 단순하고 안전한 해법임이 검증된 패턴이다.

`@Version` + 재시도는 **여러 필드를 복합적으로 검증/수정**할 때 의미 있다
(예: "재고가 0 이상일 때만 차감"). product_metrics의 증감은 단순 카운터이므로 과잉 설계다.

### 변경으로 제거된 것

| 제거 항목 | 이유 |
|----------|------|
| `@Version` 필드 | 낙관적 락 불필요 (atomic UPDATE가 동시성 보장) |
| `incrementLikeCount()` 등 엔티티 메서드 | dirty checking 미사용 (쿼리가 직접 UPDATE) |
| `getOrCreate` 패턴 | upsert가 INSERT/UPDATE를 1문장으로 처리 |
| `@Transactional` (Service) | 단일 SQL문이므로 별도 TX 관리 불필요 (Handler TX에 참여) |
| `ProductMetrics` 생성자 | 엔티티 직접 생성 불필요 (upsert가 INSERT 담당) |

### 트레이드오프

- **얻은 것**: 코드 대폭 단순화. 동시성 문제 원천 제거. 재시도/TransactionTemplate 등 복잡한 인프라 코드 불필요. DB 라운드트립 감소 (SELECT + UPDATE → 1문장).
- **잃은 것**: 비즈니스 로직이 엔티티 메서드가 아닌 SQL에 존재. JPA 표준이 아닌 MySQL 네이티브 쿼리 의존 (`ON DUPLICATE KEY UPDATE`는 MySQL 전용). 단, DB 마이그레이션 시나리오가 현실적으로 없으므로 수용.

### 참고: JPA가 atomic UPDATE를 지원하지 않는 이유

JPA의 dirty checking은 **엔티티 객체의 현재 상태를 통째로 SET**하는 구조다.
`like_count = like_count + 1` 같은 DB 표현식은 자바 객체 세계에 존재하지 않으므로 JPA가 생성할 수 없다.
카운터, 재고 차감 등 동시성에 민감한 연산은 JPA의 구조적 약점 → `@Query` 네이티브 쿼리가 필수.

---

---

# Phase 3: 선착순 쿠폰 발급 의사결정

---

## 결정 9. 파티션 키 전략 — templateId 기반 순차 처리

### 문제

선착순 쿠폰 발급에서 핵심 요구사항은 **"maxIssueCount 초과 발급 방지"**다.
같은 쿠폰 템플릿에 대한 발급 요청이 여러 스레드에서 동시에 처리되면, 수량 체크와 발급 사이 경쟁 조건으로 초과 발급이 발생할 수 있다.

### 최종 결정: **templateId를 파티션 키로 사용**

```
hash(templateId) % partitionCount = 파티션 번호
→ 같은 쿠폰 템플릿 = 같은 파티션 = 같은 컨슈머 스레드 = 순차 처리
```

같은 templateId의 요청이 반드시 하나의 스레드에서 순차 처리되므로, Consumer 내부에서 **동시성 제어 코드(@Version, 비관적 락, atomic UPDATE)가 불필요**하다.
이것이 Kafka로 선착순 쿠폰을 처리하는 핵심 이점 — **아키텍처가 동시성을 해결**한다.

### 트레이드오프

- **얻은 것**: Consumer 코드에서 동시성 제어 제거. 단순한 순차 로직으로 정확한 수량 보장.
- **잃은 것**: 인기 쿠폰에 트래픽 편중 시 hot partition 발생 가능. 단, 발급 수량이 제한적(10~1000개)이고 이벤트 기간이 짧아(몇 시간) 일시적 편중은 허용 가능. Kafka 파티션 자체는 초당 수만 건 처리 가능하므로, 실제 병목은 Consumer 처리 속도 — 이는 부하 테스트로 확인 필요.

---

## 결정 10. 발급 결과 추적 — user_coupons 상태 추가 vs 별도 테이블

### 문제

비동기 처리(Kafka) 구조에서 API가 `202 Accepted`를 반환하면, 사용자는 발급 결과를 즉시 알 수 없다.
"대기 중인지, 발급됐는지, 매진인지, 거절됐는지"를 추적할 방법이 필요하다.

### 선택지

| | A. `user_coupons`에 status 컬럼 추가 | B. 별도 `coupon_issue_results` 테이블 |
|---|---|---|
| **의미적 정합성** | "보유하지 않은 쿠폰"(PENDING, SOLD_OUT, REJECTED)이 쿠폰 테이블에 존재 | 요청 추적과 쿠폰 보유가 분리 |
| **기존 쿼리 영향** | 목록 조회에 `WHERE status = 'ISSUED'` 필터 추가 필요 | 기존 쿼리 변경 없음 |
| **상태 체계 충돌** | 발급 프로세스 상태(PENDING, REJECTED)와 쿠폰 라이프사이클 상태(AVAILABLE, USED, EXPIRED)가 혼재 | 각 테이블이 독립적 상태 체계 유지 |
| **테이블 추가** | 없음 | 1개 추가 |

### 최종 결정: **B. 별도 `coupon_issue_results` 테이블**

`user_coupons`는 "유저가 보유한 쿠폰"을 의미한다.
PENDING, SOLD_OUT, REJECTED는 쿠폰이 아니라 "발급 요청의 처리 상태"이므로, 관심사가 다르다.
현재 `UserCoupon`의 상태 체계(타임스탬프 기반: usedAt → USED, expiredAt → EXPIRED)와도 성격이 다르다.

Consumer는 같은 트랜잭션 안에서 `coupon_issue_results` 상태 업데이트 + `user_coupons` INSERT(성공 시)를 수행하여 원자성을 보장한다.

### 트레이드오프

- **얻은 것**: 기존 user_coupons 구조/쿼리 변경 없음. 요청과 쿠폰의 관심사 명확히 분리. 실패 사유(SOLD_OUT, REJECTED) 명시적 추적.
- **잃은 것**: 테이블 1개 추가. Consumer가 두 테이블에 쓰기 필요 (같은 TX).

---

## 결정 11. 수량 제한 전략 — Redis 선검증 + Consumer DB 카운터 (다층 방어)

### 문제

선착순 쿠폰에서 1만 명이 동시 요청하면, 모든 요청을 Kafka에 넣고 Consumer가 처리하면 불필요한 쓰기 부하가 발생한다.
maxIssueCount=10인데 9,990건이 Outbox INSERT → Kafka 전송 → Consumer SOLD_OUT 마킹으로 이어지는 낭비.

### 선택지

| | A. Consumer만 (Redis 없이) | B. Redis DECR 선검증 + Consumer DB 카운터 |
|---|---|---|
| **불필요한 Kafka 메시지** | 매진 후에도 모든 요청이 Kafka로 | 매진 후 즉시 차단 (Kafka에 안 넣음) |
| **매진 응답 속도** | 비동기 (Consumer 처리 후 polling) | **즉시** (Redis에서 바로 응답) |
| **인프라 의존성** | DB + Kafka | DB + Kafka + **Redis** |
| **정합성** | DB가 정답 (완벽) | Redis 근사치 + DB 정답 (보정 필요) |

### 최종 결정: **B. Redis DECR 선검증 + Consumer DB 카운터**

```
[API — Redis DECR]
  매진이면 즉시 응답 (Kafka에 안 넣음)
  재고 있으면 Outbox → Kafka

[Consumer — DB currentIssuedCount]
  순차 처리로 최종 수량 판단 (source of truth)
```

### Redis ↔ DB 정합성 전략

Redis와 DB는 서로 다른 저장소이므로 완벽한 실시간 정합성은 불가능하다 (CAP 정리).
오차의 방향을 **"안전한 쪽"(덜 발급)**으로 제한하고, 보정으로 최종 일관성을 확보한다.

| 실패 유형 | Redis 처리 | 이유 |
|----------|-----------|------|
| 비즈니스 거절 (중복 발급, 만료) | 즉시 Redis INCR (재고 복구) | 재시도해도 결과 동일, DLQ 불필요 |
| 기술적 실패 (DB 에러, 타임아웃) | DLQ → 재시도 → 최종 실패 시 Redis INCR | 재시도로 성공 가능, 섣부른 복구 방지 |
| Consumer 크래시 (INCR 전 중단) | **보정 스케줄러**가 주기적으로 DB 기준 동기화 | 분산 시스템의 근본적 한계, 최종 일관성으로 해결 |

보정 스케줄러:
```java
@Scheduled(fixedDelay = 60000)  // 1분마다
public void reconcile() {
    int remaining = template.getMaxIssueCount() - template.getCurrentIssuedCount();
    redis.set("coupon:stock:" + templateId, remaining);
}
```

### 트레이드오프

- **얻은 것**: 매진 후 불필요한 Kafka/DB 쓰기 제거. 매진 사용자에게 즉시 피드백. Consumer 부하 대폭 감소.
- **잃은 것**: Redis 인프라 의존성 추가. Redis ↔ DB 정합성 관리 비용 (보정 스케줄러). Redis 장애 시 폴백 로직 필요 (학습 단계에서는 미구현).

---

## 결정 12. 중복 발급 방지 — 다층 방어 (API best-effort + Consumer 최종 보장)

### 문제

동일 유저가 같은 쿠폰을 중복 요청할 수 있다.
비동기 구조에서는 "API에서 중복 체크 → Kafka 전송 → Consumer 처리" 사이에 시간 갭이 있어,
API 레벨 체크만으로는 동시 요청의 경쟁 조건을 완벽히 막을 수 없다.

### 최종 결정: **다층 방어**

```
[Layer 1 — API] 빠른 사전 필터 (best effort)
  ├─ 템플릿 만료? → 즉시 400
  ├─ 이미 발급? (DB 조회) → 즉시 409
  └─ Redis 매진? → 즉시 응답

[Layer 2 — Kafka] 순서 보장 버퍼
  └─ templateId 파티션 키 → 순차 처리

[Layer 3 — Consumer] 최종 권위 있는 판단 (@Transactional)
  ├─ event_handled 멱등성 체크
  ├─ currentIssuedCount >= maxIssueCount? → SOLD_OUT
  ├─ 이미 발급? → REJECTED + Redis INCR
  └─ 발급 성공 → ISSUED
```

### 왜 API 검증이 "best effort"인가

```
Thread 1: existsByUserId → false (아직 없음)
Thread 2: existsByUserId → false (아직 없음)  ← 동시 조회
Thread 1: Redis DECR → Kafka ← 둘 다 통과
Thread 2: Redis DECR → Kafka

Consumer (순차 처리):
  Thread 1 요청 → 발급 성공
  Thread 2 요청 → 중복! REJECTED + Redis INCR
```

API 레벨 체크는 대부분의 중복을 빠르게 차단하지만, 동시 요청의 틈새는 Consumer가 최종 방어한다.

### 트레이드오프

- **얻은 것**: 대부분의 중복 요청을 API에서 즉시 차단 (불필요한 Redis DECR + Kafka 메시지 방지). Consumer는 예외적 동시 요청만 처리.
- **잃은 것**: API에서 DB 조회 1회 추가 (existsByUserId). 단, 이미 기존 쿠폰 발급 로직에서 사용하던 검증이므로 신규 비용 아님.

---

## 결정 13. 발급 결과 확인 방식 — Polling

### 선택지

| | Polling | WebSocket | SSE |
|---|---|---|---|
| **구현** | REST API 1개 추가 | 연결 관리 + 메시지 프로토콜 | 단방향 스트림 |
| **인프라** | 기존 HTTP | WebSocket 서버 필요 | HTTP 연결 유지 |
| **사용자 경험** | 1~2초 지연 | 실시간 | 실시간 |
| **적합도** | 처리가 빠른 경우 (수 초 이내) | 채팅 등 양방향 | 알림 등 단방향 |

### 최종 결정: **Polling**

```
POST /coupons/{templateId}/issue → 202 Accepted + requestId
GET /coupons/issue-requests/{requestId} → PENDING / ISSUED / SOLD_OUT / REJECTED
```

선착순 쿠폰 처리는 대부분 수 초 이내에 완료되므로, 1~2초 간격 polling으로 충분하다.
추가 인프라(WebSocket 서버) 불필요하고, PG 결제 결과 확인과 동일한 패턴이다.

---

## 결정 요약

### Phase 2 — Outbox + Kafka 파이프라인

| # | 결정 사항 | 선택 | 핵심 이유 |
|---|----------|------|----------|
| 1 | Outbox 레이어 배치 | Infrastructure (DIP 미적용) | 비즈니스 규칙이 아닌 기술적 안전장치, 도메인 순수성 우선 |
| 2 | Outbox 기록 방식 | BEFORE_COMMIT 리스너 (29CM 방식) | Facade에서 인프라 관심사 제거, 관심사 분리 극대화 |
| 3 | 좋아요 이중 집계 | 의도적 공존 (API용 + 분석용) | 소비자와 보장 수준이 다른 별개의 요구사항 |
| 4 | Outbox 인덱스 | `(published_at, created_at)` 복합 | NULL 선두 정렬 활용, 미발행 건만 효율적 스캔 |
| 5 | Relay 발행 안전성 | 동기 `send().get()` + retryCount | ACK 미확인 유실 방지, 실패 추적 가능 |
| 6 | Consumer 실패 처리 | DLQ 직접 전송 (배치 리스너 유지) | 메시지 유실 방지 + 기존 배치 구조 유지 |
| 7 | 테이블 데이터 증가 | 삭제 없음 (학습 단계) | 실무 시 보존 기간 기반 배치 삭제 고려 |
| 8 | ProductMetrics 동시성 | atomic upsert (네이티브 쿼리) | 카운터에 @Version은 과잉, products 테이블과 동일 패턴 |

### Phase 3 — 선착순 쿠폰 발급

| # | 결정 사항 | 선택 | 핵심 이유 |
|---|----------|------|----------|
| 9 | 파티션 키 전략 | templateId | 같은 쿠폰 순차 처리 → 아키텍처로 동시성 해결 |
| 10 | 발급 결과 추적 | 별도 `coupon_issue_results` 테이블 | 요청과 쿠폰의 관심사 분리, 기존 구조 변경 없음 |
| 11 | 수량 제한 | Redis DECR(빠른 필터) + Consumer DB 카운터(최종 판단) | 불필요한 Kafka/DB 쓰기 제거, 즉시 매진 응답, 보정 스케줄러로 최종 일관성 |
| 12 | 중복 발급 방지 | 다층 방어 (API best-effort + Consumer 최종 보장) | 대부분 API에서 즉시 차단, 동시 요청 틈새는 Consumer가 방어 |
| 13 | 결과 확인 방식 | Polling | 처리가 수 초 이내, 추가 인프라 불필요, PG 결제와 동일 패턴 |
| 14 | Redis 재고 차감 개선 | Lua 스크립트 원자 연산 (SETNX + 조건부 DECR) | DECR→INCR 비효율 제거, 중복 틈새 완전 차단, Redis 호출 1회로 통합 |
| 15 | TX 롤백 + Redis 불일치 | 보정 스케줄러 위임 (추가 보상 코드 없음) | 앱 크래시 시 보상 콜백 미실행 → 어차피 스케줄러 필요, 코드 단순성 우선 |

---

## 결정 14. Redis 재고 차감 — DECR→INCR 패턴에서 Lua 스크립트 원자 연산으로 개선

### 문제

초기 구현에서는 Redis `DECR`로 재고를 차감하고, 결과가 음수이면 `INCR`로 복구하는 방식을 사용했다.
또한 중복 발급 체크는 DB `existsBy...` 쿼리로 수행했다.

이 방식에는 3가지 문제가 있었다:

| 문제 | 설명 |
|------|------|
| **1. 음수 노출** | `DECR → INCR`이 원자적이지 않아서, 찰나에 다른 요청이 음수 값을 읽을 수 있음 |
| **2. 중복 체크 틈새** | DB `existsBy...` 조회 시점과 Redis `DECR` 사이에 동일 유저의 2번째 요청이 통과 가능 |
| **3. 불필요한 INCR** | 매진 시 DECR(음수) → INCR(복구) 왕복이 발생. 재고가 0인데 왜 굳이 차감했다가 되돌리는가? |

### 최종 결정: **Lua 스크립트로 중복 체크 + 조건부 재고 차감을 원자적으로 통합**

```lua
-- coupon_issue_request.lua
-- KEYS[1] = coupon:issue-lock:{templateId}:{userId}  (중복 방지 락)
-- KEYS[2] = coupon:stock:{templateId}                 (재고)
-- ARGV[1] = 락 TTL (초)

-- 1. 중복 체크 (SETNX 역할)
if redis.call('EXISTS', KEYS[1]) == 1 then
    return -2  -- 이미 요청됨
end

-- 2. 재고 확인 — 0 이하면 차감하지 않고 즉시 매진 반환
local stock = tonumber(redis.call('GET', KEYS[2]))
if stock == nil or stock <= 0 then
    return -1  -- 매진
end

-- 3. 통과: 락 설정 + 재고 차감
redis.call('SET', KEYS[1], '1', 'EX', ARGV[1])
return redis.call('DECR', KEYS[2])  -- 0 이상 = 잔여 수량
```

### Before → After 비교

| | 기존 (DECR + INCR) | 개선 (Lua 스크립트) |
|---|---|---|
| **Redis 호출 횟수** | 중복 체크 0회(DB) + DECR 1회 + 실패 시 INCR 1회 = 최대 2회 | **항상 1회** |
| **중복 요청 방어** | DB `existsBy...` best-effort (동시성 틈새 있음) | Redis SETNX **원자적 방어** (틈새 없음) |
| **매진 시 부작용** | DECR로 음수 → INCR로 복구 (비원자적) | **재고 > 0일 때만 DECR** (음수 발생 불가) |
| **Race condition** | DB 체크~DECR 사이 동일 유저 2번째 요청 통과 가능 | Lua 단일 스레드 실행으로 **완전 차단** |

### Lua 스크립트가 원자적인 이유

Redis는 싱글 스레드로 명령을 처리한다.
Lua 스크립트는 Redis 서버 내부에서 하나의 명령처럼 실행되므로, 스크립트 실행 중에 다른 클라이언트의 명령이 끼어들 수 없다.
즉, EXISTS → GET → SET → DECR 4개 명령이 하나의 원자적 블록으로 실행된다.

### SETNX 락 TTL 설계 (5분)

```java
private static final long LOCK_TTL_SECONDS = 300; // 5분
```

- **목적**: 같은 유저가 동일 쿠폰을 중복 요청하는 것을 Redis에서 즉시 차단
- **5분의 근거**: Outbox Relay(1초) + Kafka 전달(수백ms) + Consumer 처리(수 초) = 정상 흐름 10초 이내. 5분은 Kafka 지연, Consumer 재시작 등 비정상 상황까지 포괄하는 안전 마진
- **TTL 만료 후**: Consumer가 이미 DB에 기록을 남겼으므로, Consumer의 `existsByCouponTemplateIdAndUserId` 체크가 최종 방어선 역할

### 트레이드오프

- **얻은 것**: Redis 호출 1회로 통합 (네트워크 라운드트립 감소). 음수 노출·중복 틈새 완전 제거. INCR 복구 로직 불필요.
- **잃은 것**: Lua 스크립트 디버깅이 Java 코드보다 어려움. Redis Cluster 환경에서는 KEYS가 같은 슬롯에 있어야 함 (현재 단일 Redis이므로 해당 없음). 팀원이 Lua에 익숙하지 않으면 유지보수 난이도 증가.

### CouponFacade 변경

```java
// Before: DB 중복 체크 + Redis DECR/INCR
if (couponIssueResultRepository.existsByUserIdAndCouponTemplateId(userId, couponTemplateId)) {
    throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청된 쿠폰입니다.");
}
long remaining = couponStockRepository.decrementStock(couponTemplateId);
if (remaining < 0) {
    couponStockRepository.incrementStock(couponTemplateId);
    throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
}

// After: Lua 원자 연산 1회
long result = couponStockRepository.tryIssueRequest(couponTemplateId, userId);
if (result == -2) throw new CoreException(ErrorType.CONFLICT, "이미 발급 요청된 쿠폰입니다.");
if (result == -1) throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰이 모두 소진되었습니다.");
```

---

## 결정 15. TX 롤백 + Redis DECR 잔류 — 구조적 한계와 대응 전략

### 문제

`CouponFacade.requestIssue()`에서 Redis Lua 스크립트가 성공(재고 차감 + 락 설정)한 뒤,
DB 트랜잭션이 롤백되면 어떻게 되는가?

```
1. Redis Lua: stock 10 → 9 (차감) + lock 설정  ✅
2. DB: CouponIssueResult INSERT              ← 여기서 실패
3. TX rollback                               ← DB는 원복
4. Redis: stock = 9, lock = 존재              ← Redis는 그대로!
```

결과:
- Redis 재고가 1 부족 (9인데 실제로는 10이어야 함)
- 해당 유저의 락이 남아있어 재요청 불가 (TTL 5분간)

### 왜 즉시 보상(TX rollback 콜백)을 하지 않는가

| 방안 | 문제 |
|------|------|
| `TransactionSynchronization.afterCompletion(ROLLBACK)` 에서 Redis INCR + DEL | 애플리케이션 크래시 시 콜백 자체가 실행되지 않음 |
| `@TransactionalEventListener(AFTER_ROLLBACK)` | 동일하게 크래시 시 미실행 |

어떤 보상 방식이든 **"앱이 살아있어야 실행된다"**는 전제 조건이 있다.
앱 크래시, OOM Kill, 컨테이너 종료 등에서는 보상이 실행되지 않으므로,
**보정 스케줄러(Reconciliation Scheduler)가 어차피 필요**하다.

### 최종 결정: **보정 스케줄러에 위임 (추가 보상 코드 없음)**

복잡도를 추가하지 않고, 이미 존재하는 보정 스케줄러(1분 간격)가 DB 기준으로 Redis를 덮어쓰도록 한다.

```java
@Scheduled(fixedDelay = 60_000)
public void reconcile() {
    // DB의 currentIssuedCount 기준으로 Redis 재고를 덮어씀
    int remaining = template.getMaxIssueCount() - template.getCurrentIssuedCount();
    redis.set("coupon:stock:" + templateId, remaining);
}
```

### 실제 영향도 분석

| 상황 | 발생 빈도 | 영향 시간 | 영향 범위 |
|------|----------|----------|----------|
| 일반적인 TX 실패 (제약조건 위반 등) | 드묾 | 최대 1분 (보정 주기) | 해당 쿠폰 재고 1개 부족 |
| 앱 크래시 | 매우 드묾 | 최대 1분 | 해당 쿠폰 재고 1개 부족 |
| 유저 락 잔류 | TTL 5분 | 최대 5분 | 해당 유저 1명만 영향 |

선착순 쿠폰의 특성상 "재고 1개가 1분간 부족"은 사실상 무영향이다.
10개짜리 쿠폰에서 9개만 발급 가능한 상태가 1분간 지속된 뒤 자동 복구된다.

### 트레이드오프

- **얻은 것**: 보상 코드 0줄. 단일 보정 메커니즘으로 모든 불일치 해결. 코드 단순성 유지.
- **잃은 것**: 최대 1분간 Redis 재고 부정확 (안전한 방향: 덜 발급). 유저 락 잔류 시 최대 5분간 해당 유저 재요청 불가 — 단, 정상적으로는 1번만 요청하므로 영향 없음.

### 실무 확장 시 고려

프로덕션에서 1분이 너무 길다면:
1. 보정 주기를 10초로 단축 (DB 부하와 트레이드오프)
2. TX rollback 콜백으로 "best-effort 즉시 보상" + 보정 스케줄러 "최종 안전망" 이중 구조
3. Redis Streams + Consumer Group으로 Redis 내에서 트랜잭션 처리 (Redis 전용 아키텍처)

---

## 참고 자료

- [트랜잭셔널 아웃박스 패턴의 실제 구현 사례 (29CM)](https://medium.com/@greg.shiny82/%ED%8A%B8%EB%9E%9C%EC%9E%AD%EC%85%94%EB%84%90-%EC%95%84%EC%9B%83%EB%B0%95%EC%8A%A4-%ED%8C%A8%ED%84%B4%EC%9D%98-%EC%8B%A4%EC%A0%9C-%EA%B5%AC%ED%98%84-%EC%82%AC%EB%A1%80-29cm-0f822fc23edb) — 29CM의 Transactional Outbox Pattern 실제 구현 사례
- [회원시스템 이벤트기반 아키텍처 구축하기 (우아한형제들)](https://techblog.woowahan.com/7835/) — 이벤트 기반 아키텍처 실무 적용 사례
- [Pattern: Transactional Outbox (microservices.io)](https://microservices.io/patterns/data/transactional-outbox.html) — Chris Richardson의 Transactional Outbox Pattern 정의
- [Scripting with Lua (Redis 공식 문서)](https://redis.io/docs/latest/develop/programmability/eval-intro/) — Redis Lua 스크립팅 소개, EVAL 명령 사용법
- [Distributed Locks with Redis (Redis 공식 문서)](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/) — Redis 분산 락(Redlock) 알고리즘과 SETNX 기반 락 패턴
- [MySQL INSERT ... ON DUPLICATE KEY UPDATE (MySQL 공식 문서)](https://dev.mysql.com/doc/refman/8.0/en/insert-on-duplicate.html) — atomic upsert 구문 레퍼런스
