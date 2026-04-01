# ApplicationEvent + Kafka 이벤트 파이프라인 — 설계 명세

---

## 1. 개요

본 문서는 7주차 과제의 구현 명세를 정의한다.

| Step | 주제 | 핵심 |
|---|---|---|
| Step 1 | ApplicationEvent로 경계 나누기 | 핵심 로직 vs 부가 로직 판단 + 트랜잭션 분리 |
| Step 2 | Kafka 이벤트 파이프라인 | Outbox → Debezium CDC → Kafka → commerce-streamer, product_metrics 집계, 멱등 처리 |
| Step 3 | 선착순 쿠폰 발급 | API → Kafka 발행 → Consumer 순차 처리, 수량 제한 동시성 제어 |

**참조 문서:**
- 05-payment-resilience.md — PG 비동기 결제 Resilience 설계 (스타일 기준)
- 09-event-review.md — 이벤트 파이프라인 아키텍트 리뷰 (분석 근거)

---

## 2. 현재 상태

### 2.1 인프라 상태

| 구성 요소 | 상태 | 비고 |
|---|---|---|
| commerce-api | Kafka 미사용 | `modules:kafka` 의존 없음, kafka.yml 미임포트 |
| commerce-streamer | DemoKafkaConsumer 1개 | `demo.internal.topic-v1` 소비만 |
| modules/kafka | 설정 완료 | KafkaTemplate, BATCH_LISTENER (manual ack, concurrency 3, max poll 3000) |
| Docker Kafka | KRaft 모드 | 단일 브로커, port 9092/19092, 토픽 자동 생성 비활성화 |
| Docker MySQL | 8.0 | binlog 미활성화 (기본값), port 3306 |
| Docker Redis | Master-Replica | port 6379/6380, AOF 영속성 |

**kafka.yml 발견된 문제점:**

| # | 문제 | 위치 | 심각도 |
|---|---|---|---|
| 1 | Consumer `value-serializer` → `value-deserializer` 오타 | kafka.yml:21 | 경미 (Converter가 대체) |
| 2 | Producer acks/idempotence 미설정 | kafka.yml:14-17 | **중요** (메시지 유실 가능) |
| 3 | 단건 처리용 Consumer Factory 부재 | KafkaConfig.java | **중요** (쿠폰 발급용) |
| 4 | Error Handler / DLQ 미설정 | KafkaConfig.java | **중요** |
| 5 | 토픽 생성 전략 없음 | N/A | 중간 |

### 2.2 좋아요 흐름

```
[LikeFacade.addLike — 단일 TX @Transactional]
  1. 상품 존재 확인 (productRepository.findById)
  2. 중복 좋아요 확인 (existsByMemberIdAndProductId)
  3. Like INSERT (likeRepository.save)
  4. Product.incrementLikeCount (SQL atomic UPDATE)   ← 부가 로직이 TX 내부
[TX commit]

[LikeController — 인라인 처리]
  5. productCachePort.evictProductDetail(productId)    ← 캐시 무효화가 Controller에 인라인
  6. productCachePort.evictProductList()
```

**문제점:**
- Like INSERT(핵심)와 likeCount UPDATE(부가/집계)가 같은 TX — 집계 실패 시 좋아요 자체 롤백
- 캐시 무효화가 Controller에 인라인 — 관심사 분리 안 됨

### 2.3 주문 흐름

```
[OrderFacade.createOrder — 단일 TX @Transactional]
  1. 상품 비관적 락 (deadlock 방지 위해 ID 정렬)
  2. 브랜드 조회 (N+1 방지)
  3. 스냅샷 생성 (OrderItem)
  4. 재고 차감 (product.decreaseStock)
  5. 쿠폰 적용 (CouponFacade.applyCouponToOrder — CAS UPDATE)
  6. 주문 저장 (Order.create)
  7. 쿠폰-주문 연결 (couponIssue.linkOrder)
[TX commit]
```

**문제점:**
- 부가 로직(판매량 집계, 알림)이 존재하지 않지만, 추가 시 TX 안에 진입할 구조
- 쿠폰 적용은 가격 계산에 직접 영향 → 핵심 로직 (분리 불가)

### 2.4 조회 흐름

```
ProductFacade.getProductDetailCached():
  L1(Caffeine) → L2(Redis) → DB → 캐시 저장

조회수 추적: 없음 (7주차에서 신규 추가)
```

### 2.5 쿠폰 구조

```
Coupon: name, discountType, discountValue, minOrderAmount, expiredAt
CouponIssue: couponId, memberId, status(AVAILABLE/USED/EXPIRED), expiredAt

수량 제한: 없음 → maxIssuanceCount, issuedCount 추가 필요
중복 발급 방지: 없음 (같은 쿠폰을 같은 유저가 여러 번 발급 가능)
인덱스: idx_coupon_issue_member_id, idx_coupon_issue_coupon_id (UNIQUE 없음)
```

---

## 3. Step 1 — ApplicationEvent 경계 분리

### 3.1 판단 프레임워크

```
핵심 로직 = "이것이 실패하면 사용자 요청 자체가 실패해야 하는가?"
  → YES: 핵심 TX 안에 유지
  → NO:  이벤트로 분리 가능

부가 로직 = "이것이 실패해도 사용자에게는 성공으로 보여야 하는가?"
  → YES: 이벤트 분리 (eventual consistency)
```

### 3.2 플로우별 핵심/부가 분리표

#### 좋아요 플로우

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| Like INSERT | **핵심** | 사용자 의도 (좋아요 누르기) | X |
| Outbox INSERT | **핵심** | Kafka 발행 보장 (같은 TX) | X |
| Product.incrementLikeCount | 부가 | 집계 실패와 무관하게 좋아요는 성공 | O |
| 캐시 무효화 | 부가 | 캐시 무효화 실패해도 좋아요는 성공 | O |

#### 주문 플로우

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| 재고 차감 | **핵심** | 재고 없으면 주문 불가 | X |
| 쿠폰 적용 | **핵심** | 할인 금액이 totalPrice 계산에 직접 영향 | X |
| 주문 저장 | **핵심** | 주문 자체 | X |
| Outbox INSERT | **핵심** | Kafka 발행 보장 (같은 TX) | X |
| 판매량 집계 | 부가 | 집계 실패해도 주문에 영향 없음 | O |

#### 조회 플로우

| 처리 | 핵심/부가 | 판단 근거 | 이벤트 분리 |
|---|---|---|---|
| 상품 데이터 반환 | **핵심** | 사용자 요청 목적 | X |
| 조회수 기록 | 부가 | 조회수 기록 실패해도 상품은 보여야 함 | O |

#### 주문 취소 플로우

| 처리 | 핵심/부가 | 이벤트 분리 |
|---|---|---|
| Order.cancel() | **핵심** | X |
| 재고 복원 | **핵심** | X (재고 복원 실패 시 데이터 불일치) |
| 쿠폰 복원 | **핵심** | X (쿠폰 복원 실패 시 고객 손해) |
| Outbox INSERT | **핵심** | X |
| 판매량 차감 집계 | 부가 | O |

### 3.3 이벤트 클래스 설계

```java
// commerce-api: com.loopers.domain.event

public record LikeCreatedEvent(
    Long productId,
    Long memberId,
    Long likeId
) {}

public record LikeRemovedEvent(
    Long productId,
    Long memberId,
    Long likeId
) {}

public record OrderCreatedEvent(
    Long orderId,
    Long memberId,
    List<OrderItemInfo> items  // productId, quantity, price
) {
    public record OrderItemInfo(Long productId, int quantity, int price) {}
}

public record OrderCancelledEvent(
    Long orderId,
    Long memberId,
    List<OrderItemInfo> items
) {
    public record OrderItemInfo(Long productId, int quantity, int price) {}
}

public record ProductViewedEvent(
    Long productId,
    Long memberId   // nullable — 비로그인 조회 허용
) {}
```

### 3.4 이벤트 리스너 설계

| 리스너 | 이벤트 | Phase | @Async | 처리 내용 | 실패 대응 |
|---|---|---|---|---|---|
| LikeCountEventListener | LikeCreated/Removed | AFTER_COMMIT | X (동기) | incrementLikeCount / decrementLikeCount | try-catch + 로그, product_metrics가 최종 보정 |
| CacheEvictionEventListener | LikeCreated/Removed | AFTER_COMMIT | X (동기) | evictProductDetail + evictProductList | try-catch + 로그, 다음 TTL 만료 시 자연 갱신 |
| ProductViewKafkaPublisher | ProductViewed | AFTER_COMMIT | **O** | KafkaTemplate.send (Outbox 미경유) | try-catch + 로그, 유실 허용 |

**incrementLikeCount를 동기로 유지하는 이유 (09 §2.7):**
- 사용자가 좋아요 직후 목록을 새로고침하면 반영되어 있기를 기대
- AFTER_COMMIT에서 best-effort로 실행하되, 실패해도 Like 자체는 이미 저장됨
- product_metrics + MetricsReconcileTasklet이 최종 정합성을 보장하는 안전망 역할

**캐시 무효화를 동기로 유지하는 이유:**
- 다음 조회 시 최신 데이터 보장 (UX)
- Redis eviction은 ~1ms — 응답 지연 무시 가능

### 3.5 이벤트 발행 위치

```java
// LikeFacade — 변경 후
@Transactional
public void addLike(Long memberId, Long productId) {
    // ... 기존 검증 ...
    Like like = likeRepository.save(new Like(memberId, productId));
    outboxRepository.save(EventOutbox.create(
        "Product", productId, "LIKE_CREATED", payload));    // Outbox INSERT (같은 TX)
    eventPublisher.publishEvent(new LikeCreatedEvent(
        productId, memberId, like.getId()));                 // AFTER_COMMIT 트리거
}

// OrderFacade — 변경 후
@Transactional
public Order createOrder(...) {
    // ... 기존 핵심 로직 (재고 차감 + 쿠폰 + 주문 저장) ...
    outboxRepository.save(EventOutbox.create(
        "Order", order.getId(), "ORDER_CREATED", payload));  // Outbox INSERT (같은 TX)
    eventPublisher.publishEvent(new OrderCreatedEvent(
        order.getId(), memberId, items));                    // AFTER_COMMIT 트리거
    return order;
}

// ProductFacade — 변경 후
public ProductDto.ProductResponse getProductDetailCached(Long productId) {
    // ... 기존 캐시 조회 로직 ...
    eventPublisher.publishEvent(new ProductViewedEvent(
        productId, memberId));                               // 조회수 이벤트 (TX 없음)
    return response;
}
```

### 3.6 @Async 스레드 풀

```java
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("event-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

**core=2, max=4 근거 (09 §13):**
- @Async 대상 작업: 조회수 Kafka 발행 (논블로킹)
- DB/Redis 커넥션 미사용 → HikariCP(max 40)과 경합 없음
- 큰 풀은 컨텍스트 스위칭만 유발
- CallerRunsPolicy → 큐 초과 시 호출 스레드에서 실행 (배압)

---

## 4. Step 2 — Kafka 이벤트 파이프라인

### 4.1 전체 아키텍처 흐름도

```
┌─────────────────── commerce-api ───────────────────┐
│                                                     │
│  [Facade]                                           │
│    │                                                │
│    ├─ [TX] 도메인 변경 + event_outbox INSERT        │
│    │        → commit                                │
│    │                                                │
│    ├─ [AFTER_COMMIT] ApplicationEvent               │
│    │    ├─ incrementLikeCount (동기, best-effort)   │
│    │    ├─ 캐시 무효화 (동기)                        │
│    │    └─ 조회수 KafkaTemplate.send (@Async)       │
│    │                                                │
│    └─ event_outbox 테이블                           │
│         ↓ (MySQL binlog)                            │
└─────────┼───────────────────────────────────────────┘
          │
   ┌──────┼──────── Kafka Connect ──────────────┐
   │  [Debezium MySQL Connector]                │
   │    └─ Outbox Event Router SMT              │
   │        → route.by.field = aggregate_type   │
   └──────┼─────────────────────────────────────┘
          │
   ┌──────┼──────── Kafka ──────────────────────┐
   │      ▼                                      │
   │  ┌─────────────────┐  ┌──────────────────┐ │
   │  │ catalog-events  │  │ order-events     │ │
   │  │ (product views, │  │ (order created,  │ │
   │  │  likes)         │  │  cancelled)      │ │
   │  └────────┬────────┘  └────────┬─────────┘ │
   │           │                    │            │
   │  ┌────────────────────────────┐             │
   │  │ coupon-issue-requests      │             │
   │  └────────┬───────────────────┘             │
   └───────────┼──────────────┼──────────────────┘
               │              │
   ┌───────────┼──────────────┼─── commerce-streamer ──┐
   │           ▼              ▼                         │
   │  [MetricsConsumer]  [CouponIssueConsumer]         │
   │    → product_metrics    → CAS UPDATE coupon       │
   │      UPSERT             → CouponIssue INSERT      │
   │    → event_handled      → event_handled           │
   └───────────────────────────────────────────────────┘
```

### 4.2 event_outbox DDL + Entity

```sql
CREATE TABLE event_outbox (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    aggregate_type  VARCHAR(50)  NOT NULL,   -- 'Product', 'Order'
    aggregate_id    BIGINT       NOT NULL,   -- productId, orderId
    event_type      VARCHAR(50)  NOT NULL,   -- 'LIKE_CREATED', 'ORDER_CREATED', ...
    payload         TEXT         NOT NULL,   -- JSON
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_event_outbox_created_at (created_at)
);
```

**status 컬럼이 없는 이유 (09 §8.5):**
Debezium이 MySQL binlog에서 직접 읽으므로 PENDING/PROCESSED 구분이 불필요하다.
Poller 방식이라면 `SELECT WHERE status = 'PENDING'`이 필요하지만, CDC 방식은 INSERT 시점에 binlog 이벤트가 발생하며 Debezium이 이를 실시간 감지한다.

```java
// commerce-api: com.loopers.domain.event

@Entity
@Table(name = "event_outbox")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EventOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static EventOutbox create(String aggregateType, Long aggregateId,
                                     String eventType, String payload) {
        EventOutbox outbox = new EventOutbox();
        outbox.aggregateType = aggregateType;
        outbox.aggregateId = aggregateId;
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.createdAt = LocalDateTime.now();
        return outbox;
    }
}
```

### 4.3 Debezium CDC 구성

#### 4.3.1 MySQL binlog 활성화

```yaml
# docker/infra-compose.yml — mysql 서비스에 command 추가
mysql:
  image: mysql:8.0
  command:
    - --log-bin=mysql-bin
    - --binlog-format=ROW
    - --binlog-row-image=FULL
    - --server-id=1
  # ... 기존 설정 유지
```

#### 4.3.2 Kafka Connect Docker 서비스

```yaml
# docker/infra-compose.yml — 서비스 추가
kafka-connect:
  image: debezium/connect:2.5
  container_name: kafka-connect
  depends_on:
    kafka:
      condition: service_healthy
    mysql:
      condition: service_started
  ports:
    - "8083:8083"
  environment:
    GROUP_ID: 1
    BOOTSTRAP_SERVERS: kafka:9092
    CONFIG_STORAGE_TOPIC: _connect_configs
    OFFSET_STORAGE_TOPIC: _connect_offsets
    STATUS_STORAGE_TOPIC: _connect_status
    CONFIG_STORAGE_REPLICATION_FACTOR: 1
    OFFSET_STORAGE_REPLICATION_FACTOR: 1
    STATUS_STORAGE_REPLICATION_FACTOR: 1
    KEY_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    VALUE_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    KEY_CONVERTER_SCHEMAS_ENABLE: "false"
    VALUE_CONVERTER_SCHEMAS_ENABLE: "false"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8083/connectors"]
    interval: 10s
    timeout: 5s
    retries: 10
```

**KRaft 모드 + Kafka Connect 내부 토픽 자동 생성 이슈:**

현재 인프라는 KRaft 모드(ZooKeeper 없음)로 Kafka를 운영한다. Kafka Connect는 시작 시 내부 토픽 3개(`_connect_configs`, `_connect_offsets`, `_connect_status`)를 자동 생성하는데, KRaft 컨트롤러가 아직 준비되지 않은 시점에 생성을 시도하면 실패할 수 있다.

**대응:**
1. `depends_on: kafka: condition: service_healthy` — Kafka 브로커의 healthcheck 통과 후 Connect 시작
2. Connect의 `healthcheck.retries: 10` — 내부 토픽 생성 재시도 여유 확보
3. 만약 Connect 시작 실패 시, 내부 토픽을 수동 생성:

```bash
# Kafka Connect 내부 토픽 수동 생성 (KRaft 환경에서 자동 생성 실패 시)
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic _connect_configs --partitions 1 --replication-factor 1 --config cleanup.policy=compact
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic _connect_offsets --partitions 25 --replication-factor 1 --config cleanup.policy=compact
docker exec kafka kafka-topics.sh --bootstrap-server localhost:9092 \
  --create --topic _connect_status --partitions 5 --replication-factor 1 --config cleanup.policy=compact
```

#### 4.3.3 Debezium MySQL Connector + Outbox Event Router SMT

```bash
#!/bin/bash
# docker/register-debezium-connector.sh

curl -X POST http://localhost:8083/connectors -H "Content-Type: application/json" -d '{
  "name": "loopers-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",

    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "root",
    "database.server.id": "184054",
    "topic.prefix": "loopers",

    "database.include.list": "loopers",
    "table.include.list": "loopers.event_outbox",

    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
    "schema.history.internal.kafka.topic": "_schema_history",

    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType",

    "tombstones.on.delete": "false"
  }
}'
```

**라우팅 결과:**

| aggregate_type | 라우팅 토픽 | event_type 예시 |
|---|---|---|
| Product | `Product-events` → 별칭: `catalog-events` | LIKE_CREATED, LIKE_REMOVED |
| Order | `Order-events` → 별칭: `order-events` | ORDER_CREATED, ORDER_CANCELLED |

> **토픽 라우팅 보완:** Debezium Outbox Event Router의 `route.topic.replacement`이 `${routedByValue}-events`로 동작하므로, aggregate_type 값을 소문자(`product`, `order`)로 저장하거나, RegexRouter SMT를 추가하여 `catalog-events`, `order-events`로 변환한다. 구현 시 최종 확정.

### 4.4 토픽 설계

| 토픽 | Key | 이벤트 유형 | Producer | Consumer |
|---|---|---|---|---|
| `catalog-events` | productId | LIKE_CREATED, LIKE_REMOVED, PRODUCT_VIEWED | Debezium + commerce-api(조회수) | commerce-streamer |
| `order-events` | orderId | ORDER_CREATED, ORDER_CANCELLED | Debezium | commerce-streamer |
| `coupon-issue-requests` | couponId | COUPON_ISSUE_REQUESTED | commerce-api (직접) | commerce-streamer |

**Key 설계 근거:**
- catalog-events key=productId → 같은 상품의 이벤트는 같은 파티션 → 순서 보장
- order-events key=orderId → 같은 주문의 이벤트는 같은 파티션
- coupon-issue-requests key=couponId → 같은 쿠폰의 발급 요청은 같은 파티션

**acks + min.insync.replicas 상관관계:**

| 설정 조합 | 의미 | 메시지 유실 | 가용성 |
|---|---|---|---|
| `acks=all` + `replicas=1` + `min.insync.replicas=1` | **현재 (개발)** — 브로커 1대뿐이므로 acks=all ≡ acks=1 | 브로커 장애 시 유실 | 높음 |
| `acks=all` + `replicas=3` + `min.insync.replicas=2` | **프로덕션 권장** — Leader + 최소 1 Follower 기록 확인 | 2대 동시 장애 아닌 한 무유실 | 1대 장애까지 허용 |
| `acks=all` + `replicas=3` + `min.insync.replicas=3` | ISR 3대 모두 기록 확인 | 무유실 | 1대라도 장애 시 쓰기 불가 |

> **핵심**: `acks=all`은 ISR(In-Sync Replicas) 전원에게 기록 확인을 요구하지만, 브로커가 1대뿐이면 `acks=1`과 동일하다. `acks=all`이 의미를 갖으려면 반드시 `min.insync.replicas ≥ 2` + `replicas ≥ 3`이 전제되어야 한다.

```java
// commerce-api: com.loopers.infrastructure.kafka

@Configuration
public class KafkaTopicConfig {

    // 개발 환경: 단일 브로커 → replicas=1
    // 프로덕션: replicas=3, min.insync.replicas=2 설정 필수
    //   → .config("min.insync.replicas", "2")

    @Bean
    public NewTopic catalogEvents() {
        return TopicBuilder.name("catalog-events")
            .partitions(3)
            .replicas(1)      // 프로덕션: .replicas(3)
            .build();
    }

    @Bean
    public NewTopic orderEvents() {
        return TopicBuilder.name("order-events")
            .partitions(3)
            .replicas(1)      // 프로덕션: .replicas(3)
            .build();
    }

    @Bean
    public NewTopic couponIssueRequests() {
        return TopicBuilder.name("coupon-issue-requests")
            .partitions(3)
            .replicas(1)      // 프로덕션: .replicas(3)
            .build();
    }
}
```

### 4.5 Producer 설정 보완

```yaml
# modules/kafka/src/main/resources/kafka.yml — producer 섹션 보완
spring:
  kafka:
    producer:
      acks: all                # 모든 ISR에 기록 확인 후 응답 → 메시지 유실 방지
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      # retries 명시하지 않음 — enable.idempotence=true 시 기본값 Integer.MAX_VALUE
      # retries를 직접 설정하면 idempotent producer의 무한 재시도 보장이 깨진다
      properties:
        enable.idempotence: true                    # Producer 레벨 중복 발행 방지
        max.in.flight.requests.per.connection: 5    # idempotence 활성화 시 최대 5
        delivery.timeout.ms: 120000                 # 재시도 포함 전체 발행 타임아웃 (2분)
        linger.ms: 50                               # 50ms 버퍼링 → 배치 효율 향상
        batch.size: 32768                           # 32KB 배치 크기
        compression.type: lz4                       # 압축 → 네트워크 I/O 감소 + Broker 디스크 절약
```

**enable.idempotence=true와 retries의 관계:**
- `enable.idempotence=true`를 설정하면 Kafka는 내부적으로 `retries=Integer.MAX_VALUE`, `max.in.flight.requests.per.connection ≤ 5`를 강제한다.
- `retries: 3`을 명시하면 idempotent producer의 기본값(MAX_VALUE)을 **덮어쓴다** → 3회 재시도 후 포기 → 메시지 유실 가능.
- 재시도 횟수 대신 `delivery.timeout.ms`(기본 120초)로 **시간 기반 제어**가 올바르다. 이 시간 내에서 무한 재시도한다.

**linger.ms + batch.size + compression.type의 원리 — Zero-Copy와 OS Page Cache:**

Kafka의 높은 처리량은 두 가지 OS 수준 최적화에 기반한다:

1. **Zero-Copy (sendfile 시스템콜)**: Broker가 Consumer에게 메시지를 전달할 때, 디스크 → 커널 버퍼 → 네트워크 소켓으로 직접 복사한다. 유저 스페이스로 데이터를 올리지 않으므로 CPU 사용량과 메모리 복사가 극적으로 줄어든다.
2. **OS Page Cache**: Broker는 메시지를 JVM 힙이 아닌 OS 페이지 캐시에 저장한다. 최근 메시지는 디스크 I/O 없이 메모리에서 바로 서빙된다.

이 두 가지 최적화의 효율을 극대화하려면 **작은 메시지를 하나씩 보내는 대신, 배치로 묶어서 보내는 것**이 핵심이다:
- `linger.ms=50`: 50ms 동안 메시지를 버퍼에 모은 뒤 한 번에 전송 → 네트워크 라운드트립 감소
- `batch.size=32768`: 32KB 단위로 배치 → Zero-Copy 시 큰 블록 전송으로 효율 증가
- `compression.type=lz4`: 배치 단위 압축 → 네트워크 I/O 감소 + Broker 디스크 절약 + 페이지 캐시 적중률 향상 (같은 메모리에 더 많은 메시지 캐싱)

### 4.6 Consumer 설정 보완

```yaml
# modules/kafka/src/main/resources/kafka.yml — consumer 섹션 수정
spring:
  kafka:
    consumer:
      group-id: loopers-default-consumer
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.ByteArrayDeserializer  # 오타 수정
      auto-offset-reset: earliest   # 신규 Consumer Group은 처음부터 읽기 (latest → 유실)
      properties:
        enable-auto-commit: false
        isolation.level: read_committed  # Debezium TX 메시지 — 커밋된 것만 읽기
```

**설정 근거:**
- `auto-offset-reset: earliest` — 신규 Consumer Group이 토픽에 처음 참여할 때 `latest`(기본값)이면 기존 메시지를 건너뛴다. 이벤트 파이프라인에서 메시지 유실은 허용 불가. `earliest`로 설정하여 처음부터 읽는다. 중복은 event_handled가 걸러낸다.
- `isolation.level: read_committed` — Debezium이 Outbox 테이블의 INSERT를 binlog에서 읽을 때, TX가 커밋되기 전의 중간 상태도 발행될 수 있다. `read_committed`는 커밋된 메시지만 Consumer에게 노출한다.

**SINGLE_LISTENER 추가 (KafkaConfig.java):**

```java
// modules/kafka — KafkaConfig.java에 추가

public static final String SINGLE_LISTENER = "SINGLE_LISTENER_DEFAULT";

@Bean(name = SINGLE_LISTENER)
public ConcurrentKafkaListenerContainerFactory<Object, Object> defaultSingleListenerContainerFactory(
        KafkaProperties kafkaProperties,
        ByteArrayJsonMessageConverter converter,
        DefaultErrorHandler errorHandler
) {
    Map<String, Object> consumerConfig = new HashMap<>(kafkaProperties.buildConsumerProperties());
    // SINGLE_LISTENER는 건별 CAS UPDATE — 처리 시간이 BATCH보다 길 수 있음
    consumerConfig.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000);  // 10분

    ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
        new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(consumerConfig));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
    factory.setMessageConverter(converter);
    factory.setConcurrency(1);
    factory.setBatchListener(false);         // 단건 처리
    factory.setCommonErrorHandler(errorHandler);
    return factory;
}
```

**max.poll.interval.ms 설정 근거:**
- SINGLE_LISTENER에서 건별 CAS UPDATE + UNIQUE INSERT + 상태 업데이트를 수행한다.
- DB 부하가 높은 시점에 처리가 지연되면, 기본값(5분) 내에 다음 poll()을 호출하지 못해 리밸런싱이 발생할 수 있다.
- 10분으로 여유를 두어 일시적 DB 지연 시에도 불필요한 리밸런싱을 방지한다.

### 4.7 Error Handler + DLQ

```java
// modules/kafka — KafkaConfig.java에 추가

@Bean
public DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(kafkaTemplate);
    // 3회 재시도, 1초 간격 고정 백오프
    return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 3));
}
```

**동작:**
1. Consumer 메시지 처리 실패 시 1초 간격으로 최대 3회 재시도
2. 3회 모두 실패 → DLT(Dead Letter Topic)로 이동 (원본 토픽명 + `.DLT`)
3. DLT 예시: `catalog-events.DLT`, `order-events.DLT`, `coupon-issue-requests.DLT`

### 4.8 조회수 직접 Kafka 발행

```java
// commerce-api: com.loopers.application.event

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductViewKafkaPublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(ProductViewedEvent event) {
        // 조회는 TX 없음 → @EventListener로도 가능하지만
        // 일관성을 위해 @TransactionalEventListener 사용 (fallbackExecution = true 고려)
        try {
            kafkaTemplate.send("catalog-events",
                String.valueOf(event.productId()),
                Map.of(
                    "eventType", "PRODUCT_VIEWED",
                    "productId", event.productId(),
                    "memberId", event.memberId(),
                    "timestamp", Instant.now().toString()
                ));
        } catch (Exception e) {
            log.warn("조회수 Kafka 발행 실패 — productId={}", event.productId(), e);
            // 유실 허용: 조회수는 정확성보다 추세가 중요
        }
    }
}
```

**Outbox 미경유 근거 (09 §3.10):**
- 조회 = 읽기 전용 (DB 쓰기 없음) → Outbox INSERT를 위한 별도 TX가 필요
- 조회마다 DB 쓰기 1건 추가 = 성능 오버헤드
- 조회수는 정확성보다 추세가 중요 (±수 건 허용)
- KafkaTemplate.send()는 내부적으로 배치 + 버퍼링 (효율적)

### 4.9 Outbox 테이블 정리

```java
// commerce-batch: MetricsReconcileTasklet 또는 별도 스케줄러

// 1시간 보존 후 Batch DELETE
@Scheduled(cron = "0 0 * * * *")  // 매 시 정각
public void cleanupOutbox() {
    int deleted = entityManager.createNativeQuery(
        "DELETE FROM event_outbox WHERE created_at < DATE_SUB(NOW(), INTERVAL 1 HOUR) LIMIT 10000"
    ).executeUpdate();
    log.info("[OutboxCleanup] 삭제 건수: {}", deleted);
}
```

**규모 산정:**
- 좋아요: 일 100만 건, 주문: 일 50만 건, 조회: Outbox 미경유
- event_outbox: 일 150만 건 (행당 ~500 bytes)
- Debezium이 binlog에서 읽으므로 테이블 누적 최소화
- 1시간 보존 기준 최대 ~6.25만 건 → Batch DELETE ~1초 이내

---

## 5. product_metrics 집계

### 5.1 product_metrics DDL

```sql
CREATE TABLE product_metrics (
    product_id    BIGINT       PRIMARY KEY,
    like_count    BIGINT       NOT NULL DEFAULT 0,
    view_count    BIGINT       NOT NULL DEFAULT 0,
    sales_count   BIGINT       NOT NULL DEFAULT 0,
    sales_amount  BIGINT       NOT NULL DEFAULT 0,
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6)
);
```

### 5.2 product_like_stats 흡수 + Product.like_count 유지 이유

**product_like_stats → product_metrics 흡수:**
- product_like_stats는 like_count만 보유 → product_metrics가 like_count + view_count + sales_count + sales_amount 통합 관리
- LikeCountSyncTasklet → MetricsReconcileTasklet로 진화
- product_like_stats 테이블은 product_metrics 마이그레이션 후 DROP

**Product.like_count 컬럼 유지 (09 §3.6):**
- 정렬 인덱스 `idx_product_like_count(like_count DESC, id DESC)`가 이 컬럼 기준
- 제거하면 좋아요순 정렬 시 product_metrics JOIN 필요 → 성능 하락
- 비정규화 캐시로 유지, MetricsReconcileTasklet이 product_metrics 기준으로 보정

### 5.3 ProductMetrics Entity

```java
// commerce-streamer: com.loopers.domain.metrics

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 5.4 MetricsConsumer

```java
// commerce-streamer: com.loopers.interfaces.consumer

@Slf4j
@Component
@RequiredArgsConstructor
public class MetricsConsumer {

    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    @KafkaListener(
        topics = {"catalog-events", "order-events"},
        groupId = "metrics-collector",    // 전용 Consumer Group
        containerFactory = KafkaConfig.BATCH_LISTENER
    )
    public void consume(
        List<ConsumerRecord<String, Map<String, Object>>> messages,
        Acknowledgment acknowledgment
    ) {
        for (ConsumerRecord<String, Map<String, Object>> record : messages) {
            String eventId = extractEventId(record);

            // INSERT-first 패턴: event_handled + 비즈니스 로직을 단일 TX로 처리
            TransactionStatus tx = transactionManager.getTransaction(
                new DefaultTransactionDefinition());
            try {
                int inserted = entityManager.createNativeQuery(
                    "INSERT IGNORE INTO event_handled (event_id) VALUES (:eventId)"
                ).setParameter("eventId", eventId).executeUpdate();

                if (inserted == 0) {
                    transactionManager.rollback(tx);
                    continue;  // 멱등: 이미 처리된 이벤트
                }

                String eventType = extractEventType(record);
                Map<String, Object> payload = record.value();

                switch (eventType) {
                    case "LIKE_CREATED" -> upsertMetrics(
                        toLong(payload.get("productId")), "like_count", 1);
                    case "LIKE_REMOVED" -> upsertMetrics(
                        toLong(payload.get("productId")), "like_count", -1);
                    case "PRODUCT_VIEWED" -> upsertMetrics(
                        toLong(payload.get("productId")), "view_count", 1);
                    case "ORDER_CREATED" -> handleOrderCreated(payload);
                    case "ORDER_CANCELLED" -> handleOrderCancelled(payload);
                    default -> log.warn("알 수 없는 이벤트 타입: {}", eventType);
                }

                transactionManager.commit(tx);
            } catch (Exception e) {
                transactionManager.rollback(tx);
                log.error("메트릭 처리 실패 — eventId={}", eventId, e);
            }
        }
        acknowledgment.acknowledge();
    }

    private void upsertMetrics(Long productId, String column, long delta) {
        entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, " + column + ", updated_at) "
                + "VALUES (:productId, :delta, NOW(6)) "
                + "ON DUPLICATE KEY UPDATE "
                + column + " = " + column + " + :delta, updated_at = NOW(6)"
        )
        .setParameter("productId", productId)
        .setParameter("delta", delta)
        .executeUpdate();
    }
}
```

**Consumer Group 분리:**
- `metrics-collector`: MetricsConsumer 전용. catalog-events, order-events 구독.
- `coupon-issuer`: CouponIssueConsumer 전용. coupon-issue-requests 구독.
- 분리 이유: 같은 group-id를 공유하면, 한 Consumer의 처리 지연이 다른 Consumer의 리밸런싱을 유발한다. 쿠폰 발급(건별 CAS)과 메트릭 집계(배치 UPSERT)는 처리 특성이 완전히 다르므로 격리해야 한다.

**BATCH_LISTENER 사용 이유:**
- catalog-events, order-events는 집계 연산 → 배치로 처리해도 정합성 문제 없음
- 3000건/poll + manual ack → 높은 처리량
- 개별 건 실패 시 배치 전체 재처리 → event_handled로 중복 방지

---

## 6. 멱등 처리

### 6.1 event_handled DDL

```sql
CREATE TABLE event_handled (
    event_id    VARCHAR(100)  PRIMARY KEY,
    handled_at  DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_event_handled_handled_at (handled_at)
);
```

### 6.2 멱등 처리 흐름

**원자성 보장 — INSERT-first 패턴:**

기존 설계(비즈니스 로직 → event_handled INSERT)의 문제: 비즈니스 로직 성공 후, event_handled INSERT 전에 Consumer가 크래시하면 → 재시작 시 같은 메시지를 다시 처리 → **비즈니스 로직 중복 실행**. UPSERT(+1, -1)처럼 멱등하지 않은 연산에서 데이터 정합성이 깨진다.

```
Consumer가 메시지 수신:
  1. event_id 추출 (record header 또는 payload)
  2. [단일 TX 시작]
     2-1. INSERT IGNORE INTO event_handled (event_id) VALUES (?)
          → affected rows = 0이면 skip (이미 처리됨) → ack → TX rollback
     2-2. 비즈니스 로직 실행 (UPSERT product_metrics 또는 CouponIssue INSERT)
  3. [TX 커밋]
  4. ack
```

**핵심: event_handled INSERT와 비즈니스 로직이 동일 트랜잭션 안에 있어야 한다.**

- event_handled INSERT를 **먼저** 시도: 중복이면 즉시 skip → 불필요한 비즈니스 로직 실행 방지
- INSERT 성공 → 비즈니스 로직 실행 → TX 커밋: 비즈니스 로직 실패 시 event_handled도 함께 롤백
- TX 커밋 후 크래시 → 재시작 시 event_handled에 이미 존재 → skip → **중복 실행 불가**

**INSERT IGNORE 패턴 vs SELECT 후 INSERT:**
- `INSERT IGNORE`: PK 중복 시 에러 없이 무시, affected rows로 판단 → 단일 쿼리 + race condition 방지
- `SELECT → INSERT`: 조회~삽입 사이에 다른 Consumer가 같은 event_id를 처리할 수 있음 → 불안전

### 6.3 event_id 생성 전략

| 소스 | event_id 형식 | 예시 |
|---|---|---|
| Debezium Outbox | `outbox:{event_outbox.id}` | `outbox:12345` |
| 직접 Kafka 발행 (조회수) | `view:{productId}:{timestamp}:{uuid 8자리}` | `view:100:1719820800000:a1b2c3d4` |
| 선착순 쿠폰 | `coupon-issue:{couponIssueRequestId}` | `coupon-issue:5678` |

### 6.4 event_handled 정리

```sql
-- 7일 보존 후 삭제 (commerce-batch 또는 스케줄러)
DELETE FROM event_handled WHERE handled_at < DATE_SUB(NOW(), INTERVAL 7 DAY) LIMIT 10000;
```

**7일 근거:**
- Kafka retention 기본값 7일 → 7일 이전 메시지는 Kafka에서도 삭제됨
- 재처리 가능 범위 = Kafka retention과 일치시킴

---

## 7. Step 3 — 선착순 쿠폰 발급

### 7.1 Coupon 모델 확장 DDL

```sql
ALTER TABLE coupon
ADD COLUMN max_issuance_count INT NULL COMMENT 'NULL이면 무제한',
ADD COLUMN issued_count INT NOT NULL DEFAULT 0;
```

### 7.2 coupon_issue UNIQUE 제약

```sql
ALTER TABLE coupon_issue
ADD UNIQUE INDEX uk_coupon_issue_coupon_member (coupon_id, member_id);
```

**근거:** 같은 쿠폰 + 같은 유저 → INSERT 시 중복이면 예외 → 거절

### 7.3 coupon_issue_request DDL + Entity

```sql
CREATE TABLE coupon_issue_request (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    coupon_id      BIGINT       NOT NULL,
    member_id      BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL DEFAULT 'PENDING',  -- PENDING / COMPLETED / REJECTED
    reject_reason  VARCHAR(100),
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at   DATETIME(6),
    INDEX idx_coupon_issue_request_member (member_id),
    INDEX idx_coupon_issue_request_coupon (coupon_id)
);
```

```java
// commerce-api: com.loopers.domain.coupon

@Entity
@Table(name = "coupon_issue_request")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "reject_reason", length = 100)
    private String rejectReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    public static CouponIssueRequest create(Long couponId, Long memberId) {
        CouponIssueRequest request = new CouponIssueRequest();
        request.couponId = couponId;
        request.memberId = memberId;
        request.status = CouponIssueRequestStatus.PENDING;
        request.createdAt = LocalDateTime.now();
        return request;
    }
}

public enum CouponIssueRequestStatus {
    PENDING, COMPLETED, REJECTED
}
```

### 7.4 발급 요청 API 흐름

```
[사용자] → POST /api/v1/coupons/{couponId}/issue-request

[commerce-api — CouponFacade.requestCouponIssue]
  1. Coupon 조회 + 만료 확인
  2. coupon_issue_request INSERT (status = PENDING)
  3. Kafka에 COUPON_ISSUE_REQUESTED 발행 (key = couponId)
     → KafkaTemplate.send("coupon-issue-requests", couponId, payload)
  4. 즉시 응답: { requestId, status: "PENDING" }
```

```java
// commerce-api: CouponFacade — 추가 메서드

@Transactional
public CouponIssueRequest requestCouponIssue(Long couponId, Long memberId) {
    Coupon coupon = couponRepository.findById(couponId)
        .orElseThrow(() -> new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다."));
    ZonedDateTime now = ZonedDateTime.now(clock);
    if (now.isAfter(coupon.getExpiredAt())) {
        throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
    }

    CouponIssueRequest request = CouponIssueRequest.create(couponId, memberId);
    couponIssueRequestRepository.save(request);

    kafkaTemplate.send("coupon-issue-requests",
        String.valueOf(couponId),
        Map.of(
            "requestId", request.getId(),
            "couponId", couponId,
            "memberId", memberId,
            "timestamp", Instant.now().toString()
        ));

    return request;
}
```

### 7.5 Consumer 처리 흐름

```
[commerce-streamer — CouponIssueConsumer (SINGLE_LISTENER, groupId=coupon-issuer)]

  [단일 TX 시작]
  1. INSERT IGNORE INTO event_handled (event_id = "coupon-issue:{requestId}")
     → affected rows = 0 → skip (이미 처리됨) → TX rollback + ack

  2. CAS UPDATE — 수량 확인 + 발급 카운트 증가
     UPDATE coupon
     SET issued_count = issued_count + 1
     WHERE id = :couponId
       AND issued_count < max_issuance_count
       AND deleted_at IS NULL;
     → affected rows = 0 → 수량 소진 → REJECTED

  3. CouponIssue INSERT (중복 발급 방지)
     INSERT INTO coupon_issue (coupon_id, member_id, status, expired_at, created_at)
     VALUES (:couponId, :memberId, 'AVAILABLE', :expiredAt, NOW());
     → DuplicateKeyException (uk_coupon_issue_coupon_member) → 이미 발급 → REJECTED

  4. coupon_issue_request 상태 업데이트
     UPDATE coupon_issue_request
     SET status = 'COMPLETED', completed_at = NOW()
     WHERE id = :requestId;

  [TX 커밋] → ack

  * 비즈니스 로직 실패 시 event_handled도 함께 롤백 → 재시도 가능
```

```java
// commerce-streamer: com.loopers.interfaces.consumer

@Slf4j
@Component
@RequiredArgsConstructor
public class CouponIssueConsumer {

    private final EntityManager entityManager;
    private final PlatformTransactionManager transactionManager;

    @KafkaListener(
        topics = "coupon-issue-requests",
        groupId = "coupon-issuer",        // 전용 Consumer Group
        containerFactory = KafkaConfig.SINGLE_LISTENER  // 단건 처리 — 개별 에러 핸들링
    )
    public void consume(ConsumerRecord<String, Map<String, Object>> record,
                        Acknowledgment acknowledgment) {
        Map<String, Object> payload = record.value();
        Long requestId = toLong(payload.get("requestId"));
        String eventId = "coupon-issue:" + requestId;
        Long couponId = toLong(payload.get("couponId"));
        Long memberId = toLong(payload.get("memberId"));

        // INSERT-first 패턴: event_handled + 비즈니스 로직을 단일 TX로 처리
        TransactionStatus tx = transactionManager.getTransaction(
            new DefaultTransactionDefinition());
        try {
            int inserted = entityManager.createNativeQuery(
                "INSERT IGNORE INTO event_handled (event_id) VALUES (:eventId)"
            ).setParameter("eventId", eventId).executeUpdate();

            if (inserted == 0) {
                transactionManager.rollback(tx);
                acknowledgment.acknowledge();
                return;  // 멱등: 이미 처리됨
            }

            processCouponIssue(requestId, couponId, memberId);
            transactionManager.commit(tx);
        } catch (Exception e) {
            transactionManager.rollback(tx);
            log.error("쿠폰 발급 처리 실패 — requestId={}", requestId, e);
            // 실패 시 event_handled도 롤백됨 → 재시도 가능
            // DLQ로 이동 시 rejectRequest 처리는 ErrorHandler에서 수행
        }

        acknowledgment.acknowledge();
    }

    private void processCouponIssue(Long requestId, Long couponId, Long memberId) {
        // CAS UPDATE — 수량 확인 + 발급 카운트 증가
        int updated = entityManager.createNativeQuery(
            "UPDATE coupon SET issued_count = issued_count + 1 "
                + "WHERE id = :couponId AND issued_count < max_issuance_count "
                + "AND deleted_at IS NULL"
        ).setParameter("couponId", couponId).executeUpdate();

        if (updated == 0) {
            rejectRequest(requestId, "수량 소진");
            return;
        }

        // CouponIssue INSERT
        try {
            entityManager.createNativeQuery(
                "INSERT INTO coupon_issue (coupon_id, member_id, status, expired_at, created_at) "
                    + "SELECT :couponId, :memberId, 'AVAILABLE', c.expired_at, NOW() "
                    + "FROM coupon c WHERE c.id = :couponId"
            ).setParameter("couponId", couponId)
             .setParameter("memberId", memberId)
             .executeUpdate();
        } catch (Exception e) {
            // UNIQUE 제약 위반 → 이미 발급됨 → issued_count 롤백
            entityManager.createNativeQuery(
                "UPDATE coupon SET issued_count = issued_count - 1 WHERE id = :couponId"
            ).setParameter("couponId", couponId).executeUpdate();
            rejectRequest(requestId, "이미 발급된 쿠폰");
            return;
        }

        // 성공 상태 업데이트
        entityManager.createNativeQuery(
            "UPDATE coupon_issue_request SET status = 'COMPLETED', completed_at = NOW() "
                + "WHERE id = :requestId"
        ).setParameter("requestId", requestId).executeUpdate();
    }
}
```

### 7.6 동시성 제어 — Kafka만으로 부족한 이유 + DB CAS가 핵심

```
오해: "key=couponId → 같은 파티션 → 순차 소비 → 동시성 해결"

현실 (09 §4.3):
  1. Consumer 장애 → Rebalancing → 메시지 재처리 (At Least Once)
     → 같은 요청이 2번 처리될 수 있음
  2. Consumer Group 내 파티션 재할당 중 중복 소비 가능
  3. 배치 리스너의 경우 동일 couponId의 여러 요청이 같은 배치에 포함

결론:
  Kafka = "폭주 요청 버퍼링 + 순서 힌트" (부하 완충)
  DB CAS UPDATE = "수량 제어의 핵심" (정확성 보장)
  UNIQUE 제약 = "중복 발급 방지의 최종 방어선"
```

**SINGLE_LISTENER 사용 이유 (09 §11):**
- 건별 CAS UPDATE + 개별 에러 핸들링이 필요
- BATCH_LISTENER에서 배치 내 부분 실패 처리가 복잡
- 쿠폰 발급은 집계와 달리 건별 정확성이 중요

### 7.7 결과 확인 — Polling

```
[사용자] → GET /api/v1/coupons/issue-requests/{requestId}
  → coupon_issue_request 조회
  → { requestId, status: "PENDING" | "COMPLETED" | "REJECTED", rejectReason }
```

**Polling 선택 근거 (09 §4.7):**
- 구현 단순, 인프라 추가 불필요
- 쿠폰 발급은 수 초 내 완료 → 1~2회 polling이면 충분
- SSE/WebSocket은 커넥션 유지 오버헤드

---

## 8. Redis 설정 보완

```java
// modules/redis: RedisConfig.java — lettuceConnectionFactory 메서드 수정

private LettuceConnectionFactory lettuceConnectionFactory(
        int database,
        RedisNodeInfo master,
        List<RedisNodeInfo> replicas,
        Consumer<LettuceClientConfiguration.LettuceClientConfigurationBuilder> customizer
) {
    LettuceClientConfiguration.LettuceClientConfigurationBuilder builder =
        LettuceClientConfiguration.builder()
            .commandTimeout(Duration.ofMillis(500));  // ← 추가
    if (customizer != null) customizer.accept(builder);
    // ... 이하 동일
}
```

**근거 (09 §12.2):**
- 현재: 타임아웃 미설정 → Redis 장애 시 스레드 무한 대기 가능
- 정상 응답 ~1ms, 500ms 초과 = 장애 판단
- Lettuce NIO multiplexing이므로 커넥션 풀 불필요 — 타임아웃만으로 보호

---

## 9. 전체 흐름 통합

### 9.1 좋아요 분리 후 전체 흐름도

```
[사용자] POST /api/v1/products/{productId}/likes
  │
  ▼
[LikeFacade.addLike — TX]
  Like INSERT + event_outbox INSERT
  → TX commit
  │
  ├─ [AFTER_COMMIT — 동기]
  │    ├─ Product.incrementLikeCount (best-effort)
  │    └─ 캐시 무효화 (evictProductDetail + evictProductList)
  │
  └─ [event_outbox — MySQL binlog]
       → Debezium → catalog-events 토픽
         → [commerce-streamer] MetricsConsumer
           → product_metrics.like_count UPSERT
           → event_handled INSERT
```

### 9.2 주문 분리 후 전체 흐름도

```
[사용자] POST /api/v1/orders
  │
  ▼
[OrderFacade.createOrder — TX]
  재고 차감 + 쿠폰 적용 + 주문 저장 + event_outbox INSERT
  → TX commit
  │
  └─ [event_outbox — MySQL binlog]
       → Debezium → order-events 토픽
         → [commerce-streamer] MetricsConsumer
           → product_metrics.sales_count / sales_amount UPSERT (상품별)
           → event_handled INSERT
```

### 9.3 조회수 전체 흐름도

```
[사용자] GET /api/v1/products/{productId}
  │
  ▼
[ProductFacade.getProductDetailCached]
  L1/L2 캐시 → DB → 응답
  │
  └─ [ApplicationEvent — ProductViewedEvent]
       → [ProductViewKafkaPublisher — @Async]
         → KafkaTemplate.send("catalog-events", productId, payload)
           → [commerce-streamer] MetricsConsumer
             → product_metrics.view_count UPSERT
             → event_handled INSERT

* Outbox 미경유 — 읽기 전용 연산, 유실 허용
```

### 9.4 선착순 쿠폰 전체 흐름도

```
[사용자] POST /api/v1/coupons/{couponId}/issue-request
  │
  ▼
[CouponFacade.requestCouponIssue — TX]
  Coupon 검증 + coupon_issue_request INSERT (PENDING)
  → KafkaTemplate.send("coupon-issue-requests", couponId, payload)
  → 즉시 응답: { requestId, status: "PENDING" }
  │
  └─ [Kafka — coupon-issue-requests 토픽]
       → [commerce-streamer] CouponIssueConsumer (SINGLE_LISTENER)
         → event_handled 확인 (멱등)
         → CAS UPDATE coupon.issued_count (수량 확인)
         → INSERT coupon_issue (UNIQUE 제약)
         → UPDATE coupon_issue_request (COMPLETED / REJECTED)
         → event_handled INSERT

[사용자] GET /api/v1/coupons/issue-requests/{requestId}
  → 결과 확인 (Polling)
```

### 9.5 주문 취소 전체 흐름도

```
[사용자] DELETE /api/v1/orders/{orderId}
  │
  ▼
[OrderFacade.cancelOrder — TX]
  order.cancel() + 재고 복원 + 쿠폰 복원 + event_outbox INSERT
  → TX commit
  │
  └─ [event_outbox — MySQL binlog]
       → Debezium → order-events 토픽
         → [commerce-streamer] MetricsConsumer
           → product_metrics.sales_count / sales_amount 차감 (상품별)
           → event_handled INSERT
```

---

## 10. 정합성 안전망

### 10.1 MetricsReconcileTasklet (LikeCountSyncTasklet 진화)

```java
// commerce-batch: com.loopers.batch.job.metricsreconcile.step

@Slf4j
@RequiredArgsConstructor
@Component
public class MetricsReconcileTasklet implements Tasklet {

    private final EntityManager entityManager;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        // 1단계: likes 테이블 기준 → product_metrics.like_count 보정
        log.info("[MetricsReconcile] 1단계: like_count 대사 시작");
        int likeCorrected = entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, like_count, updated_at) "
                + "SELECT l.product_id, COUNT(*), NOW(6) FROM likes l GROUP BY l.product_id "
                + "ON DUPLICATE KEY UPDATE like_count = VALUES(like_count), updated_at = NOW(6)"
        ).executeUpdate();
        log.info("[MetricsReconcile] 1단계 완료 — 대사 행 수: {}", likeCorrected);

        // 2단계: product_metrics.like_count → Product.like_count 비정규화 보정
        log.info("[MetricsReconcile] 2단계: Product.like_count 드리프트 보정 시작");
        int productCorrected = entityManager.createNativeQuery(
            "UPDATE product p JOIN product_metrics pm ON p.id = pm.product_id "
                + "SET p.like_count = pm.like_count "
                + "WHERE p.like_count != pm.like_count AND p.deleted_at IS NULL"
        ).executeUpdate();
        log.info("[MetricsReconcile] 2단계 완료 — 보정된 상품 수: {}", productCorrected);

        // 3단계: order_items 기준 → product_metrics.sales_count/sales_amount 보정
        log.info("[MetricsReconcile] 3단계: sales_count/sales_amount 대사 시작");
        int salesCorrected = entityManager.createNativeQuery(
            "INSERT INTO product_metrics (product_id, sales_count, sales_amount, updated_at) "
                + "SELECT oi.product_id, SUM(oi.quantity), SUM(oi.price * oi.quantity), NOW(6) "
                + "FROM order_items oi JOIN orders o ON oi.order_id = o.id "
                + "WHERE o.status != 'CANCELLED' AND o.deleted_at IS NULL "
                + "GROUP BY oi.product_id "
                + "ON DUPLICATE KEY UPDATE "
                + "sales_count = VALUES(sales_count), sales_amount = VALUES(sales_amount), "
                + "updated_at = NOW(6)"
        ).executeUpdate();
        log.info("[MetricsReconcile] 3단계 완료 — 대사 행 수: {}", salesCorrected);

        return RepeatStatus.FINISHED;
    }
}
```

### 10.2 3중 안전망

```
[1차] best-effort 즉시 반영
  → AFTER_COMMIT에서 incrementLikeCount (동기)
  → 실패해도 Like 자체는 저장됨

[2차] Kafka 집계
  → Debezium → catalog-events → MetricsConsumer
  → product_metrics에 정확한 이벤트 기반 집계

[3차] 배치 대사
  → MetricsReconcileTasklet
  → 원본 데이터(likes, order_items) 기준 전수 대사
  → product_metrics 보정 + Product.like_count 비정규화 보정
```

---

## 11. 패키지 구조

### 11.1 commerce-api 패키지 트리

```
apps/commerce-api/src/main/java/com/loopers/
├── application/
│   ├── coupon/
│   │   ├── CouponFacade.java              [변경] requestCouponIssue 추가
│   │   └── CouponApplyResult.java
│   ├── like/
│   │   └── LikeFacade.java                [변경] incrementLikeCount 제거, outbox + event 발행
│   ├── order/
│   │   └── OrderFacade.java               [변경] outbox + event 발행 추가
│   ├── product/
│   │   ├── ProductFacade.java             [변경] 조회수 이벤트 발행 추가
│   │   └── ProductCachePort.java
│   └── event/                             [신규]
│       └── ProductViewKafkaPublisher.java  [신규] 조회수 직접 Kafka 발행
├── domain/
│   ├── coupon/
│   │   ├── Coupon.java                    [변경] maxIssuanceCount, issuedCount 추가
│   │   ├── CouponIssue.java
│   │   ├── CouponIssueRequest.java        [신규]
│   │   ├── CouponIssueRequestStatus.java  [신규]
│   │   └── CouponIssueRequestRepository.java [신규]
│   ├── event/                             [신규]
│   │   ├── LikeCreatedEvent.java          [신규]
│   │   ├── LikeRemovedEvent.java          [신규]
│   │   ├── OrderCreatedEvent.java         [신규]
│   │   ├── OrderCancelledEvent.java       [신규]
│   │   ├── ProductViewedEvent.java        [신규]
│   │   ├── EventOutbox.java               [신규]
│   │   └── EventOutboxRepository.java     [신규]
│   └── ...
├── infrastructure/
│   ├── coupon/
│   │   └── CouponIssueRequestJpaRepository.java [신규]
│   ├── event/                             [신규]
│   │   └── EventOutboxJpaRepository.java  [신규]
│   ├── kafka/                             [신규]
│   │   ├── KafkaTopicConfig.java          [신규] NewTopic 빈 정의
│   │   └── AsyncConfig.java               [신규] @Async 스레드 풀
│   └── ...
├── interfaces/
│   ├── api/
│   │   ├── coupon/
│   │   │   └── CouponController.java      [변경] 발급 요청/결과 확인 API 추가
│   │   ├── like/
│   │   │   └── LikeController.java        [변경] 캐시 무효화 인라인 코드 제거
│   │   └── ...
│   └── listener/                          [신규]
│       ├── LikeCountEventListener.java    [신규] AFTER_COMMIT → incrementLikeCount
│       └── CacheEvictionEventListener.java [신규] AFTER_COMMIT → 캐시 무효화
└── ...
```

### 11.2 commerce-streamer 패키지 트리

```
apps/commerce-streamer/src/main/java/com/loopers/
├── CommerceStreamerApplication.java
├── domain/                                [신규]
│   ├── metrics/                           [신규]
│   │   ├── ProductMetrics.java            [신규] streamer 자체 Entity
│   │   └── ProductMetricsRepository.java  [신규]
│   └── event/                             [신규]
│       ├── EventHandled.java              [신규] streamer 자체 Entity
│       └── EventHandledRepository.java    [신규]
└── interfaces/
    └── consumer/
        ├── DemoKafkaConsumer.java          (기존 유지)
        ├── MetricsConsumer.java            [신규] catalog-events + order-events
        └── CouponIssueConsumer.java        [신규] coupon-issue-requests
```

**commerce-streamer에서 Native SQL 사용 근거:**
- CouponIssueConsumer가 coupon, coupon_issue, coupon_issue_request 테이블에 접근
- 이들은 commerce-api의 도메인 Entity → streamer에서 Entity를 공유하면 모듈 결합도 증가
- commerce-batch의 LikeCountSyncTasklet이 `entityManager.createNativeQuery()`로 접근하는 기존 패턴 준수
- Native SQL로 최소한의 접근만 수행 (CAS UPDATE, INSERT, status UPDATE)

### 11.3 commerce-batch 패키지

```
apps/commerce-batch/src/main/java/com/loopers/batch/job/
├── likecountsync/                         [변경 → metricsreconcile로 리네임]
│   ├── LikeCountSyncJobConfig.java        [변경] → MetricsReconcileJobConfig.java
│   └── step/
│       └── LikeCountSyncTasklet.java      [변경] → MetricsReconcileTasklet.java
├── outboxcleanup/                         [신규]
│   ├── OutboxCleanupJobConfig.java        [신규]
│   └── step/
│       └── OutboxCleanupTasklet.java      [신규] event_outbox 1시간 보존 DELETE
├── eventhandledcleanup/                   [신규]
│   ├── EventHandledCleanupJobConfig.java  [신규]
│   └── step/
│       └── EventHandledCleanupTasklet.java [신규] event_handled 7일 보존 DELETE
├── paymentrecovery/                       (기존 유지)
└── reconciliation/                        (기존 유지)
```

---

## 12. 의존성 + Docker 변경

### 12.1 commerce-api: `modules:kafka` 추가

```kotlin
// apps/commerce-api/build.gradle.kts
dependencies {
    // ... 기존 의존성 ...
    implementation(project(":modules:kafka"))  // 추가 — 조회수 직접 Kafka 발행용
}
```

### 12.2 infra-compose.yml 변경

```yaml
# 1. mysql 서비스: binlog 활성화 command 추가
mysql:
  image: mysql:8.0
  command:
    - --log-bin=mysql-bin
    - --binlog-format=ROW
    - --binlog-row-image=FULL
    - --server-id=1
  # ... 기존 ports, environment, volumes 유지

# 2. kafka-connect 서비스 추가 (§4.3.2 참조)
kafka-connect:
  image: debezium/connect:2.5
  container_name: kafka-connect
  depends_on:
    kafka:
      condition: service_healthy
    mysql:
      condition: service_started
  ports:
    - "8083:8083"
  environment:
    GROUP_ID: 1
    BOOTSTRAP_SERVERS: kafka:9092
    CONFIG_STORAGE_TOPIC: _connect_configs
    OFFSET_STORAGE_TOPIC: _connect_offsets
    STATUS_STORAGE_TOPIC: _connect_status
    CONFIG_STORAGE_REPLICATION_FACTOR: 1
    OFFSET_STORAGE_REPLICATION_FACTOR: 1
    STATUS_STORAGE_REPLICATION_FACTOR: 1
    KEY_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    VALUE_CONVERTER: org.apache.kafka.connect.json.JsonConverter
    KEY_CONVERTER_SCHEMAS_ENABLE: "false"
    VALUE_CONVERTER_SCHEMAS_ENABLE: "false"
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:8083/connectors"]
    interval: 10s
    timeout: 5s
    retries: 10
```

### 12.3 Debezium Connector 등록 스크립트

```bash
# docker/register-debezium-connector.sh
# infra-compose up 이후 실행

#!/bin/bash
set -e

echo "Waiting for Kafka Connect to be ready..."
until curl -s http://localhost:8083/connectors > /dev/null 2>&1; do
  sleep 2
done

echo "Registering Debezium MySQL Connector..."
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @- << 'EOF'
{
  "name": "loopers-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "tasks.max": "1",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "root",
    "database.password": "root",
    "database.server.id": "184054",
    "topic.prefix": "loopers",
    "database.include.list": "loopers",
    "table.include.list": "loopers.event_outbox",
    "schema.history.internal.kafka.bootstrap.servers": "kafka:9092",
    "schema.history.internal.kafka.topic": "_schema_history",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.id": "id",
    "transforms.outbox.table.field.event.key": "aggregate_id",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.event.payload": "payload",
    "transforms.outbox.route.by.field": "aggregate_type",
    "transforms.outbox.route.topic.replacement": "${routedByValue}-events",
    "transforms.outbox.table.fields.additional.placement": "event_type:header:eventType",
    "tombstones.on.delete": "false"
  }
}
EOF

echo "Connector registered successfully!"
curl -s http://localhost:8083/connectors/loopers-outbox-connector/status | python3 -m json.tool
```

---

## 13. 구현 계획

### Phase 1: ApplicationEvent 기반 분리

| # | 항목 | 대상 파일 |
|---|---|---|
| 1 | 이벤트 record 5개 생성 | domain/event/*.java |
| 2 | EventOutbox Entity + Repository | domain/event/, infrastructure/event/ |
| 3 | LikeFacade에서 incrementLikeCount 제거, Outbox INSERT + 이벤트 발행 | LikeFacade.java |
| 4 | LikeCountEventListener (AFTER_COMMIT, 동기) | interfaces/listener/ |
| 5 | CacheEvictionEventListener (AFTER_COMMIT, 동기) | interfaces/listener/ |
| 6 | LikeController에서 캐시 무효화 인라인 코드 제거 | LikeController.java |
| 7 | @Async 스레드 풀 설정 | infrastructure/kafka/AsyncConfig.java |

### Phase 2: Kafka 인프라 + Debezium

| # | 항목 | 대상 파일 |
|---|---|---|
| 8 | commerce-api build.gradle.kts에 `modules:kafka` 추가 | build.gradle.kts |
| 9 | kafka.yml Producer 보완 (acks, idempotence, linger.ms) | kafka.yml |
| 10 | kafka.yml Consumer value-deserializer 오타 수정 | kafka.yml |
| 11 | SINGLE_LISTENER ContainerFactory 추가 | KafkaConfig.java |
| 12 | DefaultErrorHandler + DLQ 설정 | KafkaConfig.java |
| 13 | NewTopic 빈 3개 선언 | KafkaTopicConfig.java |
| 14 | infra-compose.yml MySQL binlog command 추가 | infra-compose.yml |
| 15 | infra-compose.yml kafka-connect 서비스 추가 | infra-compose.yml |
| 16 | Debezium Connector 등록 스크립트 | docker/register-debezium-connector.sh |
| 17 | RedisConfig commandTimeout(500ms) 추가 | RedisConfig.java |

### Phase 3: product_metrics Consumer

| # | 항목 | 대상 파일 |
|---|---|---|
| 18 | event_outbox DDL 실행 | DDL |
| 19 | product_metrics DDL 실행 | DDL |
| 20 | event_handled DDL 실행 | DDL |
| 21 | ProductMetrics Entity (commerce-streamer) | domain/metrics/ |
| 22 | EventHandled Entity (commerce-streamer) | domain/event/ |
| 23 | MetricsConsumer (BATCH_LISTENER) | interfaces/consumer/ |
| 24 | ProductViewKafkaPublisher (@Async, 직접 Kafka) | application/event/ |
| 25 | ProductFacade 조회수 이벤트 발행 추가 | ProductFacade.java |
| 26 | OrderFacade Outbox INSERT + 이벤트 발행 추가 | OrderFacade.java |
| 27 | OrderFacade.cancelOrder Outbox INSERT 추가 | OrderFacade.java |

### Phase 4: 선착순 쿠폰

| # | 항목 | 대상 파일 |
|---|---|---|
| 28 | Coupon 모델 확장 DDL (max_issuance_count, issued_count) | DDL + Coupon.java |
| 29 | coupon_issue UNIQUE 제약 추가 DDL | DDL |
| 30 | coupon_issue_request DDL + Entity | domain/coupon/ |
| 31 | CouponFacade.requestCouponIssue 추가 | CouponFacade.java |
| 32 | CouponController 발급 요청/결과 확인 API 추가 | CouponController.java |
| 33 | CouponIssueConsumer (SINGLE_LISTENER, Native SQL) | interfaces/consumer/ |

### Phase 5: 배치 + 테스트

| # | 항목 | 대상 파일 |
|---|---|---|
| 34 | MetricsReconcileTasklet (LikeCountSyncTasklet 진화) | commerce-batch |
| 35 | OutboxCleanupTasklet (1시간 보존 DELETE) | commerce-batch |
| 36 | EventHandledCleanupTasklet (7일 보존 DELETE) | commerce-batch |
| 37 | ApplicationEvent 분리 단위 테스트 | commerce-api/test |
| 38 | Kafka Consumer 통합 테스트 (EmbeddedKafka) | commerce-streamer/test |
| 39 | 선착순 쿠폰 동시성 테스트 | commerce-streamer/test |
| 40 | Debezium E2E 테스트 (Testcontainers) | 통합 테스트 |

---

## 14. 전체 DDL 요약

### 신규 테이블 (4개)

| 테이블 | 용도 | 위치 |
|---|---|---|
| `event_outbox` | Debezium CDC용 Outbox | commerce-api TX 내 INSERT |
| `product_metrics` | 상품 집계 (좋아요, 조회수, 판매량) | commerce-streamer UPSERT |
| `event_handled` | 멱등 처리 (중복 이벤트 방지) | commerce-streamer INSERT |
| `coupon_issue_request` | 선착순 쿠폰 발급 요청 추적 | commerce-api INSERT, commerce-streamer UPDATE |

### 변경 테이블 (2개)

| 테이블 | 변경 내용 |
|---|---|
| `coupon` | `max_issuance_count INT NULL`, `issued_count INT DEFAULT 0` 컬럼 추가 |
| `coupon_issue` | `UNIQUE INDEX uk_coupon_issue_coupon_member (coupon_id, member_id)` 추가 |

### 삭제 테이블 (1개)

| 테이블 | 사유 |
|---|---|
| `product_like_stats` | product_metrics로 흡수 (마이그레이션 후 DROP) |

---

## 핵심 설계 결정 요약

| 결정 | 내용 | 근거 (09 참조) |
|---|---|---|
| Debezium CDC | Poller 대신 binlog 기반 Outbox 발행 | §8 — 학습 가치 + 중복 발행 원천 해결 |
| event_outbox에 status 없음 | Debezium이 binlog에서 읽으므로 PENDING/PROCESSED 불필요 | §8.5 |
| incrementLikeCount 동기 | AFTER_COMMIT에서 best-effort, @Async 아님 | §2.7, §13 |
| @Async core=2, max=4 | DB/Redis 미사용 초경량 작업이므로 작은 풀 | §13 |
| SINGLE_LISTENER for 쿠폰 | 건별 CAS UPDATE + 개별 에러 핸들링 | §11 |
| commerce-streamer Native SQL | Coupon/CouponIssue 접근 시 Entity 미사용 | commerce-batch 기존 패턴 |
| Product.like_count 유지 | 정렬 인덱스 유지, 제거 시 성능 하락 | §3.6 |
| 조회수 Outbox 미경유 | 읽기 전용 → TX 없음 → 직접 Kafka 발행 | §3.10 |
