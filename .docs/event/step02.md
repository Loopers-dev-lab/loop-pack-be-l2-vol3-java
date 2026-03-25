# Step 2 — Kafka 이벤트 파이프라인 설계

## 목표

`commerce-api → Kafka → commerce-streamer` 구조로 이벤트를 전파한다.
`commerce-streamer`는 이벤트를 수신해 `product_metrics`(좋아요 수 / 판매량)를 집계한다.

---

## 토픽 설계

| 토픽 | 이벤트 | Partition Key | 순서 보장 단위 |
|------|--------|--------------|--------------|
| `catalog-events` | `LIKE_CREATED`, `LIKE_DELETED` | productId | 같은 상품의 좋아요 이벤트 |
| `order-events` | `ORDER_CREATED` | orderId | 같은 주문의 이벤트 |

---

## Producer — Transactional Outbox Pattern

### 왜 Outbox Pattern인가?
- Kafka 발행은 외부 I/O → DB commit 이후 실패 시 이벤트 유실 가능
- outbox_event를 DB에 같은 트랜잭션으로 기록하면, DB commit = 이벤트 보존 보장
- 별도 relay 스케줄러가 미발행 이벤트를 Kafka로 전송 → At Least Once 발행 보장

### 흐름
```
LikeFacade / OrderFacade
  → ApplicationEventPublisher (in-process, 기존 유지)
      → BEFORE_COMMIT handler: outbox_event 테이블에 UUID + payload 기록 (같은 트랜잭션)
  → DB commit
  → OutboxEventRelayScheduler (1초 주기)
      → published_at IS NULL 이벤트 조회
      → KafkaTemplate으로 발행 (acks=all, idempotence=true)
      → published_at 갱신
```

### Producer 설정
- `acks=all` — 모든 ISR에서 수신 확인
- `enable.idempotence=true` — 네트워크 재시도 시 중복 발행 방지

---

## Consumer — commerce-streamer

### 흐름
```
CatalogEventConsumer / OrderEventConsumer (batch, manual Ack)
  → event_handled(event_id PK) 존재 확인
      → 있으면 skip (중복 이벤트)
  → product_metrics upsert
      → occurredAt > updatedAt 인 경우만 반영 (최신 이벤트만)
  → event_handled 저장
  → Ack
```

### product_metrics 집계 대상
| 이벤트 | 집계 항목 |
|--------|---------|
| `LIKE_CREATED` | likeCount + 1 |
| `LIKE_DELETED` | likeCount - 1 |
| `ORDER_CREATED` | orderCount + 판매 수량 |

---

## 멱등성 설계

두 가지 중복 시나리오를 구분해서 처리한다:

| 시나리오 | 처리 위치 | 방법 |
|----------|----------|------|
| 따닥 좋아요 → 도메인 액션 2번 | Producer 이전 (도메인) | `like(userId, productId) UNIQUE` 제약 — 2번째 INSERT 실패 → outbox 미기록 |
| 같은 outbox 레코드 relay 2번 발행 | Consumer | `event_handled(event_id PK)` — 동일 UUID skip |

- `eventId`(UUID)는 outbox 저장 시점에 생성되어 payload에 포함됨
- Consumer는 UUID 기준으로만 멱등 처리

---

## 이벤트 페이로드

### catalog-events
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440000",
  "eventType": "LIKE_CREATED | LIKE_DELETED",
  "userId": 1,
  "productId": 42,
  "occurredAt": "2026-03-25T10:00:00Z"
}
```

### order-events
```json
{
  "eventId": "550e8400-e29b-41d4-a716-446655440001",
  "eventType": "ORDER_CREATED",
  "userId": 1,
  "orderId": "20260325-ABCDEF",
  "totalAmount": 90000,
  "occurredAt": "2026-03-25T10:00:00Z"
}
```

---

## 주요 신규 컴포넌트

### commerce-api
- `OutboxEvent` (Entity) — topic, partitionKey, payload, publishedAt
- `LikeOutboxEventHandler` / `OrderOutboxEventHandler` — BEFORE_COMMIT, outbox 기록
- `OutboxEventRelayScheduler` — 1초 주기, KafkaTemplate 발행

### commerce-streamer
- `ProductMetrics` (Entity) — productId(PK), likeCount, orderCount, updatedAt
- `EventHandled` (Entity) — eventId(PK, UUID), handledAt
- `CatalogEventConsumer` / `OrderEventConsumer` — batch, manual Ack
- `ProductMetricsFacade` — upsert + idempotency
