# Kafka 이벤트 파이프라인 학습 노트

> 이 문서는 실제 프로젝트(loop-pack-be-l2-vol3-java) 구현 과정에서 나온 대화를 정리한 학습 자료입니다.

---

## 0. 시작 전: 설계 결정 사항 정리

### Q&A로 결정된 것들

| 질문 | 결정 | 이유 |
|------|------|------|
| commerce-collector는 새 앱인가? | commerce-streamer 재활용 | 빠르게 시작, 기존 Kafka 설정 재사용 |
| Outbox 릴레이는 어디? | commerce-batch | 책임 분리, API 서버와 릴레이 분리 |
| Kafka 발행 대상 이벤트 | like/payment/view 3종 | product_metrics 집계 목적 |
| product_metrics DB 위치 | commerce-api와 동일 DB | 단순성 |
| 기존 LikeEventListener 처리 | 유지 + Kafka 병렬 | 마이그레이션 과도기 |
| Idempotent Consumer | 포함 | At Least Once 구조에서 중복 방어 필수 |
| sales_count 기준 | PaymentCompleted (결제 기준) | 실제 결제 완료된 것만 판매량으로 집계 |
| view_count Outbox 여부 | 직접 Kafka 발행 (유실 허용) | 조회 API는 트랜잭션 없음, 고트래픽 DB write 부담 |

---

## 1. 전체 아키텍처

```
[commerce-api] Producer 측
  ┌──────────────────────────────────────────────────────────┐
  │ LikeService.addLike()                                    │
  │   → Like 저장 + OutboxEvent(LIKE_CREATED) 저장 [같은 TX] │
  │                                                          │
  │ PaymentFacade.handleCallback()                           │
  │   → Payment 완료 + OutboxEvent(PRODUCT_SOLD) 저장 [같은 TX]│
  │     (OrderItemRepository로 productId 조회)              │
  │                                                          │
  │ ProductsV1Controller.getProduct()                        │
  │   → kafkaTemplate.send() 직접 발행 [Outbox 없음]         │
  └──────────────────────────────────────────────────────────┘
           outbox_events 테이블 (status: PENDING)
                         ↓
[commerce-batch] Relay 측 (5초마다)
  ┌────────────────────────────────────────────────────────┐
  │ KafkaOutboxRelay.relay()                               │
  │   ① PENDING 이벤트 조회 (JdbcTemplate)                │
  │   ② Kafka 발행 (kafkaTemplate.send().get())            │
  │   ③ 발행 성공 → status = SENT                         │
  │   ④ 발행 실패 → PENDING 유지 → 다음 실행에서 재시도    │
  └────────────────────────────────────────────────────────┘
                         ↓
           Kafka Topics
           ├── product.like.events
           ├── product.payment.events
           └── product.view.events
                         ↓
[commerce-streamer] Consumer 측
  ┌──────────────────────────────────────────────────────────┐
  │ ProductMetricsConsumer.consume()                         │
  │   → ProductMetricsService.handle()                       │
  │     ① event_handled 테이블에서 중복 여부 확인             │
  │     ② eventType에 따라 product_metrics upsert           │
  │     ③ EventHandled 저장 [같은 TX]                        │
  └──────────────────────────────────────────────────────────┘
           product_metrics 테이블 (like/sales/view count)
```

---

## 2. 핵심 개념: Transactional Outbox Pattern

### 문제: 왜 kafkaTemplate.send()를 서비스에서 바로 안 쓰는가?

```java
// ❌ 문제가 있는 코드
@Transactional
public Like addLike(Long userId, Long productId) {
    Like like = likeRepository.save(new Like(userId, productId));
    kafkaTemplate.send("product.like.events", ...); // ← 여기서 Kafka가 다운되면?
    return like;
}
```

```
시나리오: DB commit 성공 + Kafka 발행 실패
결과: Like는 DB에 저장됐지만 Kafka에는 이벤트 없음 → 영구 유실
```

### 해결: Outbox Pattern

```java
// ✅ Outbox Pattern
@Transactional
public Like addLike(Long userId, Long productId) {
    Like like = likeRepository.save(new Like(userId, productId));
    outboxEventRepository.save(OutboxEvent.create("LIKE_CREATED", topic, payload));
    // Like와 OutboxEvent가 같은 TX → 둘 다 commit 또는 둘 다 rollback
    return like;
}
```

**핵심**: DB 저장과 "이벤트 기록"을 하나의 트랜잭션으로 묶는다.
Kafka 발행은 나중에 Relay가 책임진다.

---

## 3. 핵심 개념: At Least Once와 릴레이 순서

### 릴레이 코드

```java
// KafkaOutboxRelay.relay()
kafkaTemplate.send(topic, eventId, message).get(5, TimeUnit.SECONDS);  // ① Kafka 발행
jdbcTemplate.update("UPDATE outbox_events SET status = 'SENT' ...");    // ② SENT 마킹
```

### 순서가 왜 이렇게 되어야 하는가?

```
① 발행 성공 → ② SENT 마킹 성공 → 정상
① 발행 성공 → 서버 재시작 → ② 못 함 → 이벤트는 여전히 PENDING
  → 재시작 후 릴레이가 다시 발행 → Kafka에 같은 메시지 2번 → 중복 (At Least Once)

반대 순서로 하면?
② SENT 마킹 → ① 발행 실패 → 이벤트 영구 유실 (At Most Once, 더 위험)
```

**결론**: 유실보다 중복이 낫다. 중복은 Consumer가 처리한다.

---

## 4. 핵심 개념: Idempotent Consumer (멱등 소비자)

### 문제: like_count += 1은 멱등하지 않다

```
같은 이벤트 2번 처리 → like_count가 2 증가 → 잘못된 집계
```

### 해결: EventHandled 테이블

```java
// ProductMetricsService.handle()
if (eventHandledRepository.existsByEventId(message.eventId())) {
    return;  // 이미 처리됨 → 스킵
}
productMetricsRepository.incrementLikeCount(payload.productId());
eventHandledRepository.save(new EventHandled(message.eventId()));  // 처리 완료 기록
```

```sql
-- event_handled 테이블
CREATE TABLE event_handled (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(36) NOT NULL,
    UNIQUE KEY uk_event_id (event_id)  -- ← UNIQUE 제약이 최후 방어선
);
```

**동시성 처리**: Consumer 2개가 동시에 같은 eventId를 처리하려 하면, `INSERT event_handled`에서 하나는 `DataIntegrityViolationException` 발생 → catch해서 스킵.

---

## 5. upsert: ON DUPLICATE KEY UPDATE

`product_metrics` 테이블은 `product_id`가 PRIMARY KEY입니다.
데이터가 없으면 INSERT, 있으면 UPDATE해야 합니다.

```sql
-- like_count 증가
INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at)
VALUES (:productId, 1, 0, 0, NOW())
ON DUPLICATE KEY UPDATE like_count = like_count + 1, updated_at = NOW();

-- like_count 감소 (0 이하로 내려가지 않도록 GREATEST 사용)
INSERT INTO product_metrics (product_id, like_count, sales_count, view_count, updated_at)
VALUES (:productId, 0, 0, 0, NOW())
ON DUPLICATE KEY UPDATE like_count = GREATEST(0, like_count - 1), updated_at = NOW();
```

---

## 6. ProductViewedEvent가 Outbox 없이 직접 발행되는 이유

```java
// ProductsV1Controller.getProduct()
kafkaTemplate.send(OutboxEventTopics.PRODUCT_VIEW, new KafkaOutboxMessage(...))
```

| 항목 | 설명 |
|------|------|
| 왜 Outbox 안 씀? | GET 요청 → 트랜잭션 없음 → Outbox 저장하려면 DB write 필요 |
| 고트래픽 문제 | 상품 조회마다 DB write → 부담 |
| 허용 가능한 이유 | 조회수는 정확한 수치보다 트렌드 파악이 목적 → 일부 유실 허용 |
| 보장 수준 | At Most Once (최대 1번 전달) |

---

## 7. PaymentFacade에서 OutboxEvent를 저장하는 이유

`PaymentCompletedEvent`에는 `orderId`만 있고 `productId`가 없습니다.
`product_metrics.sales_count`는 상품별 집계이므로 `productId`가 필요합니다.

```java
// PaymentService — 도메인 서비스, OrderItemRepository 없음
public void handleCallback(Long orderId, ...) {
    payment.complete(transactionId);
    // productId를 모름 → OutboxEvent 저장 불가
}

// PaymentFacade — 애플리케이션 서비스, 여러 Repository 접근 가능
@Transactional
public void handleCallback(Long orderId, String transactionId, boolean success) {
    paymentService.handleCallback(orderId, transactionId, success);
    if (success) {
        orderService.confirmOrder(orderId);
        // OrderItemRepository로 상품별 OutboxEvent 저장 가능
        orderItemRepository.findAllByOrderId(orderId).forEach(item ->
            outboxEventRepository.save(OutboxEvent.create(
                "PRODUCT_SOLD",
                OutboxEventTopics.PRODUCT_PAYMENT,
                serialize(new ProductSoldPayload(item.getProductId(), orderId))
            ))
        );
    }
}
```

**포인트**: 도메인 서비스(`PaymentService`)는 단일 도메인에만 집중. 여러 도메인을 조합해야 할 때는 Facade(애플리케이션 서비스)가 담당.

---

## 8. KafkaTemplate이란?

```java
// 직접 Kafka Producer API:
producer.send(new ProducerRecord<>(topic, key, value));

// KafkaTemplate (Spring 추상화):
kafkaTemplate.send(topic, key, value);
```

`JdbcTemplate`이 JDBC를 감싸는 것처럼, `KafkaTemplate`이 Kafka Producer API를 감쌉니다.
직렬화, 에러 핸들링, 설정 관리 등을 자동으로 처리해줍니다.

`KafkaConfig`에서 빈으로 등록됩니다:

```java
@Bean
public KafkaTemplate<Object, Object> kafkaTemplate(ProducerFactory<Object, Object> producerFactory) {
    return new KafkaTemplate<>(producerFactory);
}
```

`@Autowired`나 생성자 주입으로 어디서든 꺼내 쓸 수 있습니다.

---

## 9. Consumer 메시지 처리 흐름

```java
// ProductMetricsConsumer (Kafka → Service 위임)
@KafkaListener(
    topics = {"product.like.events", "product.payment.events", "product.view.events"},
    groupId = "product-metrics-consumer",
    containerFactory = KafkaConfig.BATCH_LISTENER
)
public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
    for (ConsumerRecord<Object, Object> message : messages) {
        try {
            byte[] valueBytes = (byte[]) message.value();
            KafkaOutboxMessage kafkaMessage = objectMapper.readValue(valueBytes, KafkaOutboxMessage.class);
            productMetricsService.handle(kafkaMessage);
        } catch (DataIntegrityViolationException e) {
            // 중복 eventId → 정상적인 스킵
        } catch (Exception e) {
            log.warn("처리 실패. 다음 메시지로 진행.");
        }
    }
    acknowledgment.acknowledge();  // ← 수동 커밋: 모든 처리 끝난 후 offset 커밋
}
```

**수동 커밋(`AckMode.MANUAL`)을 쓰는 이유**:
자동 커밋이면 메시지를 받자마자 offset이 커밋됨 → 처리 중 서버 다운 시 메시지 유실.
수동 커밋은 `acknowledgment.acknowledge()` 호출 시에만 offset 커밋 → 처리 완료 보장.

---

## 10. 생성된 파일 목록

### commerce-api
- `domain/outbox/OutboxStatus.java` — enum: PENDING, SENT
- `domain/outbox/OutboxEvent.java` — Outbox 엔티티
- `domain/outbox/OutboxEventTopics.java` — 토픽 상수
- `domain/outbox/KafkaOutboxMessage.java` — Kafka 메시지 포맷
- `domain/outbox/ProductSoldPayload.java` — 판매 이벤트 페이로드
- `domain/outbox/OutboxEventRepository.java` — 인터페이스
- `infrastructure/outbox/OutboxEventJpaRepository.java`
- `infrastructure/outbox/OutboxEventRepositoryImpl.java`
- `domain/like/LikeService.java` — **수정**: Outbox 저장 추가
- `application/payment/PaymentFacade.java` — **수정**: @Transactional + Outbox 저장
- `interfaces/api/product/ProductsV1Controller.java` — **수정**: 직접 Kafka 발행

### commerce-batch
- `batch/relay/KafkaOutboxRelay.java` — @Scheduled 릴레이
- `config/SchedulingConfig.java` — @EnableScheduling

### commerce-streamer
- `support/kafka/KafkaOutboxMessage.java` — Kafka 메시지 포맷 (중복 정의)
- `domain/metrics/ProductMetrics.java` — product_metrics 엔티티
- `domain/metrics/ProductMetricsRepository.java`
- `domain/metrics/ProductMetricsService.java` — 멱등 처리 + upsert
- `domain/idempotency/EventHandled.java` — 처리 완료 기록 엔티티
- `domain/idempotency/EventHandledRepository.java`
- `infrastructure/metrics/ProductMetricsJpaRepository.java` — native SQL upsert
- `infrastructure/metrics/ProductMetricsRepositoryImpl.java`
- `infrastructure/idempotency/EventHandledJpaRepository.java`
- `infrastructure/idempotency/EventHandledRepositoryImpl.java`
- `interfaces/consumer/ProductMetricsConsumer.java` — Kafka 리스너

### 테스트
- `domain/outbox/OutboxEventTest.java` — 단위 테스트
- `domain/like/LikeServiceOutboxIntegrationTest.java` — 통합 테스트

---

## 11. 설계 리뷰: 이슈 및 한계

### ⚠️ 이슈 1: EventHandled 테이블 무한 증가
처리된 이벤트 ID가 계속 쌓임. 운영 환경에서는 오래된 레코드 정리 정책이 필요.

### ⚠️ 이슈 2: 릴레이 동시 실행 시 중복 발행
PENDING 이벤트를 `SKIP LOCKED` 없이 조회 → 두 릴레이가 같은 이벤트를 동시에 발행 가능.
Consumer의 EventHandled로 결과는 정합성을 보장하지만, Kafka에 중복 메시지 발행됨.
해결: `SELECT ... FOR UPDATE SKIP LOCKED` 사용.

### ⚠️ 이슈 3: Kafka 발행 실패 시 무한 재시도 가능성
PENDING인 이벤트가 계속 재시도됨. 특정 이벤트가 계속 실패하면 블로킹 발생.
해결: retry_count 컬럼 추가, 일정 횟수 초과 시 DEAD 상태로 격리.

### ⚠️ 이슈 4: At Most Once인 view 이벤트
ProductViewedEvent는 Outbox 없이 직접 발행 → Kafka 다운 시 유실.
허용 가능한 이유: 조회수는 정확도보다 트렌드 파악 목적.

---

## 12. 흐름 요약

```
Step 1 (ApplicationEvent): 같은 프로세스 내 이벤트 분리
  → 한계: 서버 재시작 시 Async 스레드 이벤트 유실

Step 2 (Transactional Outbox + Kafka): 프로세스 경계를 넘어 이벤트 전달
  → 해결: DB에 이벤트 기록 → 재시작 후에도 복구 가능
  → 한계: At Least Once → Consumer가 멱등하게 처리해야 함

Idempotent Consumer: EventHandled 테이블로 중복 처리 방지
  → 보장: 같은 이벤트를 2번 받아도 결과는 동일
```