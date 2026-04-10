## 📌 Summary

- **배경**: 이벤트마다 Redis에 직접 `ZINCRBY`를 호출하는 구조에서 `ranking:all:{yyyyMMdd}` 단일 key에 write가 집중되는 Hot Key 문제가 있었다. 인기 상품에 이벤트가 몰릴수록 Redis 단일 스레드의 병목이 심화된다.
- **추가 문제**: 도메인 처리 + `EventHandled` 저장 이후 Redis flush 실패 시, Kafka 재전달이 와도 `EventHandled`로 인해 중복 이벤트로 간주되어 랭킹 delta가 복구 불가능하게 유실된다.
- **목표**: `Kafka batch → 메모리 집계 → Redis pipeline flush` 구조로 전환하여 Redis write 횟수를 이벤트 수 → 상품 종류 수로 줄이고, `RankingDeltaPending` 영속 저장으로 flush 전 장애 시에도 delta를 복구 가능한 상태로 유지한다.
- **결과**: `flush → ack` 원칙 적용, pipeline `ZINCRBY + EXPIRE`, `RankingDeltaPending(PENDING → FLUSHED)` 상태 관리, 계층별 책임 분리 완료. 랭킹 Page 조회 / 상품 상세 순위 포함 API 구현 및 테스트 완료.

---

## 🧭 Context & Decision

### 1. ack 시점을 언제로 잡을 것인가?

| 항목 | ack → flush | flush → ack (채택) |
|------|-------------|-------------------|
| flush 실패 시 | 이미 ack → Kafka 재전달 없음 → delta 유실 | ack 안 함 → Kafka 재전달 → 복구 가능 |
| 중복 가능성 | 없음 | flush 성공 + flushed 처리 전 장애 시 중복 가능 |
| 신뢰 모델 | 유실 허용 | 유실보다 중복 감수 |

- **결정**: `flush → markAsFlushed → ack`
- **근거**: 랭킹 점수 유실은 복구가 어렵다. 중복 flush는 Redis `ZINCRBY` 특성상 점수 과잉 반영에 그치며, `RankingDeltaPending` 상태 추적으로 중복 창구를 특정 장애 구간으로 한정할 수 있다.
- **트레이드오프**: Redis flush 성공 후 `flushed` DB 업데이트 전 장애 발생 시 중복 flush 가능성이 남는다. 이는 "유실 방지 > 중복 0 보장" 원칙에 따른 의식적인 선택이다.

### 2. EventHandled와 RankingDeltaPending을 왜 분리하는가?

| 역할 | EventHandled | RankingDeltaPending |
|------|-------------|---------------------|
| 의미 | "이 메시지의 도메인 처리는 끝났다" | "이 메시지의 랭킹 점수는 아직 Redis 반영 전일 수 있다" |
| 저장 시점 | 메시지 처리 트랜잭션 내 | 메시지 처리 트랜잭션 내 (동시 커밋) |
| 제거 시점 | 보존 (재전달 중복 방지용) | flush 성공 후 FLUSHED 전환 |

- **결정**: 두 테이블을 분리하여 "도메인 처리 완료"와 "Redis 반영 완료"를 독립적으로 추적
- **근거**: 하나의 개념으로 합치면 `EventHandled` 조회 시 delta 유실 여부를 판단할 수 없다. 분리함으로써 Kafka 재전달 시 도메인 중복 반영 없이 pending delta만 선택적으로 재flush할 수 있다.

### 3. flush 트리거를 어떻게 설계할 것인가?

| 항목 | 시간 기반 flush (scheduler) | Kafka poll batch 경계 flush (채택) |
|------|--------------------------|----------------------------------|
| flush 경계 | 시간 주기 (1초, 5초 등) | Kafka poll batch 단위 |
| ack 연계 | flush 성공과 ack를 묶기 어렵다 | flush 후 바로 ack — 경계가 명확 |
| 구현 복잡도 | scheduler + consumer 이원화 | consumer 단일 흐름 |
| 장애 복구 | 복잡 | poll batch 단위로 재전달 |

- **결정**: Kafka poll batch 경계에서만 flush (`MAX_POLL_RECORDS=3000`, `FETCH_MAX_WAIT_MS=5000`)
- **근거**: batch listener가 자연스러운 flush 경계를 제공한다. `flush → ack`와 가장 정합성이 높고, 장애 복구 흐름이 단순하다. 랭킹 특성상 5초 지연은 허용 범위 내다.

### 4. Redis flush를 어떻게 구현할 것인가?

| 항목 | 이벤트별 ZINCRBY | pipeline ZINCRBY (채택) |
|------|----------------|------------------------|
| Redis write 횟수 | 이벤트 수 | 상품 종류 수 |
| RTT | 이벤트 수만큼 발생 | pipeline으로 1회 |
| EXPIRE | 이벤트마다 걸림 | touched key마다 1회 |

- **결정**: 메모리 집계 후 `(날짜, 상품)` 기준으로 `ZINCRBY`를 pipeline으로 묶어 전송, key마다 `EXPIRE` 1회
- **근거**: 배치 내 동일 상품의 이벤트를 합산하면 Redis write 횟수가 상품 종류 수로 줄어든다. pipeline은 network round-trip을 1회로 압축해 throughput을 향상시킨다.

### 5. Consumer 예외 처리 원칙을 어떻게 바꿀 것인가?

| 항목 | 이전 (예외 삼킴 + 무조건 ack) | 변경 (예외 전파 + no ack) |
|------|----------------------------|--------------------------|
| 메시지 처리 실패 | 로그만 남기고 계속 진행 | throw → batch 실패 |
| flush 실패 | 무조건 ack | ack 없음 → Kafka 재전달 |
| DataIntegrityViolationException | consumer에서 catch | service의 existsByEventId가 선처리 → 제거 |

- **결정**: 실패 시 예외를 consumer까지 전파, ack를 마지막에 한 번만 호출
- **근거**: "실패를 숨기지 않는 방향"이 flush 배치 구조에 맞다. `EventHandled` unique constraint 중복은 서비스의 `existsByEventId()` 선확인으로 처리되므로 consumer 레벨 catch가 불필요하다.

### 6. 배치 집계 로직을 어디에 둘 것인가?

| 항목 | Consumer 직접 구현 | RankingFlushBatchService 분리 (채택) |
|------|------------------|-------------------------------------|
| Consumer 역할 | 배치 진행 + 집계 + flush + DB 업데이트 | 배치 진행만 (orchestration) |
| 테스트 분리 | consumer 전체 통합 테스트만 가능 | 집계/flush 로직 단위 테스트 가능 |
| 단일 책임 | 위반 | 준수 |

- **결정**: `RankingFlushBatchService.flushProcessedEvents(List<String> eventIds)`로 추출
- **근거**: Consumer는 "Kafka batch를 받아서 서비스에 넘기고 결과를 ack하는" 배치 진행자 역할만 가진다. pending 조회, 집계, Redis flush, flushed 처리를 한 서비스로 묶어 테스트와 책임 경계를 명확히 한다.

---

## 🔍 Review Points

> **[핵심] 배치 집계를 통한 Hot Key 병목 완화**
>
> `ranking:all:{yyyyMMdd}`는 하루치 이벤트 전체가 단일 key에 집중되는 구조입니다. Redis는 single-threaded이기 때문에, 이 key에 write가 몰릴수록 Redis가 병목이 된다는 점이 걱정되었습니다.
>
> 기존 구조에서는 이벤트 1건마다 Redis write가 1회씩 발생했습니다.
>
> ```
> [기존] batch 4건 → Redis write 4회
> 상품1 조회 +0.1  → ZINCRBY ranking:all:20260410 1 0.1
> 상품1 조회 +0.1  → ZINCRBY ranking:all:20260410 1 0.1
> 상품1 좋아요 +0.2 → ZINCRBY ranking:all:20260410 1 0.2
> 상품2 구매 +0.6  → ZINCRBY ranking:all:20260410 2 0.6
> ```
>
> 그래서 `RankingFlushBatchService`에서 배치 단위로 `(date, productId)` 기준의 메모리 집계를 먼저 수행한 뒤, pipeline으로 한 번에 flush하는 방식으로 바꾸었습니다. **Redis write 횟수가 이벤트 수에서 배치 내 unique 상품 종류 수로 줄어들게 됩니다.**
>
> ```
> [변경] batch 4건 → Redis write 2회 (상품 종류 수)
> 메모리 집계:
>   상품1 → 0.1 + 0.1 + 0.2 = 0.4
>   상품2 → 0.6
>
> pipeline:
>   ZINCRBY ranking:all:20260410 0.4 1
>   ZINCRBY ranking:all:20260410 0.6 2
>   EXPIRE  ranking:all:20260410 172800
> ```
>
> 트래픽이 특정 상품에 집중될수록 집계 효과가 더 커집니다. 인기 상품 1개에 배치 3000건이 몰리더라도 Redis write는 1회에 그치게 됩니다. 또한 pipeline으로 묶어 network round-trip도 1회로 압축할 수 있었습니다. → [`RankingFlushBatchService`][flush-batch-service], [`RankingRepositoryImpl`][ranking-repo-impl]

> **delta 유실 창구에 대한 의식적인 선택**
>
> 이 파이프라인 전체의 설계 방향은 "유실보다 중복"입니다. 이 방향성은 발행 측과 소비 측 모두에 일관되게 적용되어 있습니다.
>
> 발행 측 (Outbox)                                          
>  LIKE / PAYMENT → OutboxEvent(DB) → KafkaOutboxRelay → Kafka  ← 유실 방지  
>   VIEW           → kafkaTemplate.send() 직접               ← 유실 허용 (가중치 0.1, 빈번)
>
> 소비 측 (at-least-once + EventHandled)  
> Kafka 재전달 → EventHandled가 도메인 중복 반영 차단  
> flush → ack → flush 실패 시 Kafka 재전달로 delta 복구  
>
> 남은 창구  
> flush 성공 + markAsFlushed 실패 → 중복 flush → 의식적 허용  
> 
> **발행 측** — 좋아요·결제 이벤트는 Kafka로 보내기 전에 같은 트랜잭션 안에서 DB(`outbox_event`)에 먼저 저장합니다. 서버가 Kafka 전송 직전에 죽어도 DB에 기록이 남아 있어서 `KafkaOutboxRelay` 배치가 이후에 재전송합니다. 조회 이벤트는 이 과정 없이 Kafka로 바로 보내고 있는데, 랭킹 점수 기여도가 0.1로 가장 낮고 워낙 빈번하게 발생하기 때문에 일부가 빠져도 허용 가능한 수준으로 판단했습니다.
>
> **소비 측** — Kafka는 기본적으로 같은 메시지를 두 번 이상 전달할 수 있습니다(at-least-once). 같은 이벤트가 두 번 오더라도 `EventHandled` 테이블이 이미 처리한 이벤트를 걸러주기 때문에 DB에 점수가 두 번 반영되지는 않습니다. 그리고 `flush → ack` 순서 덕분에 Redis 반영에 실패하면 ack를 하지 않아 Kafka가 재전달하고, 다음 번에 다시 flush를 시도합니다.
>
> **남은 위험 가능성** — Redis flush는 성공했지만 바로 직후 `markAsFlushed` 저장 전에 장애가 나는 경우입니다. 이때 재시도가 들어오면 같은 delta가 한 번 더 Redis에 쌓입니다. `ZINCRBY`는 호출할 때마다 값이 더해지는 연산이기 때문에 점수가 의도보다 높게 올라갑니다. 이 가능성은 인지하고 있지만, 파이프라인 전체가 "유실보다 중복을 허용한다"는 방향으로 설계되어 있기 때문에 이번 구현에서는 별도 보호 장치 없이 그대로 두기로 결정했습니다. → [`LikeService`][like-service], [`PaymentFacade`][payment-facade], [`RankingFlushBatchService`][flush-batch-service]

> **날짜 경계 이벤트 처리**
>
> 하나의 Kafka batch 안에 `occurredAt` 기준으로 날짜가 다른 이벤트가 섞여 들어올 수 있다는 점을 고려했습니다. 집계 키를 `(date, productId)`로 구성하여, 날짜가 다른 이벤트는 자연스럽게 다른 ZSET key에 분리되어 반영되도록 처리했습니다. → [`RankingDeltaPending`][delta-pending]
```java
// ProductMetricsService.handle()                                                                                                
LocalDate rankingDate = occurredAt.toLocalDate();  // 이벤트 발생 시점 기준                                                      
rankingDeltaPendingRepository.save(                       
new RankingDeltaPending(message.eventId(), rankingDate, ...)
);

// RankingFlushBatchService (flush 시)
deltaByDateAndProduct
.computeIfAbsent(delta.getRankingDate(), k -> new HashMap<>())  // 날짜별 분리
.merge(delta.getProductId(), delta.getDelta(), Double::sum);
```
---

## ✅ Checklist

### 📈 Ranking Consumer

- [x] 랭킹 ZSET 의 TTL, 키 전략을 적절하게 구성하였다
  - `ranking:all:{yyyyMMdd}`, TTL 2일 → [`RankingRepositoryImpl`][ranking-repo-impl]
- [x] 날짜별로 적재할 키를 계산하는 기능을 만들었다
  - `occurredAt.toLocalDate()` → `(date, productId)` 집계 키 → [`RankingDeltaPending`][delta-pending]
- [x] 이벤트가 발생한 후, ZSET 에 점수가 적절하게 반영된다
  - Consumer flush 구조 → [`ProductMetricsConsumer`][metrics-consumer], [`RankingFlushBatchService`][flush-batch-service]

### ⚾ Ranking API

- [x] 랭킹 Page 조회 시 정상적으로 랭킹 정보가 반환된다
  - `GET /api/v1/rankings` → [`RankingV1Controller`][ranking-controller]
- [x] 랭킹 Page 조회 시 단순히 상품 ID 가 아닌 상품정보가 Aggregation 되어 제공된다
  - Product + Brand 조인 후 `productName`, `brandName`, `price` 포함 → [`RankingFacade`][ranking-facade]
- [x] 상품 상세 조회 시 해당 상품의 순위가 함께 반환된다 (순위에 없다면 null)
  - `getRank(...).orElse(null)` → [`ProductsV1Controller`][products-controller], [`ProductV1Dto`][product-dto]

### 🧪 검증

- [x] 이벤트 발행 → ZSET 점수 반영 → API 조회까지 E2E 흐름이 정상 동작하는지 확인
  - Consumer 배치 흐름 → [`ProductMetricsConsumerIntegrationTest`][consumer-test]
  - ZSET → API → [`RankingV1ApiE2ETest`][ranking-e2e-test]
- [x] 일자가 변경되어도 이전 날짜의 랭킹 조회가 정상적으로 동작하는지 확인
  - → [`RankingV1ApiE2ETest#returnsPreviousDayRanking`][ranking-e2e-test]
- [x] 가중치 적용이 의도대로 랭킹 순서에 반영되는지 확인 (e.g. 주문 1건 > 좋아요 3건)
  - → [`RankingV1ApiE2ETest#orderedProductRanksHigherThanThreeLikes`][ranking-e2e-test]

[like-service]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/domain/like/LikeService.java
[payment-facade]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/application/payment/PaymentFacade.java
[ranking-repo-impl]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-streamer/src/main/java/com/loopers/infrastructure/ranking/RankingRepositoryImpl.java
[delta-pending]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-streamer/src/main/java/com/loopers/domain/ranking/RankingDeltaPending.java
[metrics-consumer]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-streamer/src/main/java/com/loopers/interfaces/consumer/ProductMetricsConsumer.java
[flush-batch-service]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-streamer/src/main/java/com/loopers/domain/ranking/RankingFlushBatchService.java
[ranking-controller]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/interfaces/api/ranking/RankingV1Controller.java
[ranking-facade]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/application/ranking/RankingFacade.java
[products-controller]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/interfaces/api/product/ProductsV1Controller.java
[product-dto]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/main/java/com/loopers/interfaces/api/product/ProductV1Dto.java
[consumer-test]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-streamer/src/test/java/com/loopers/interfaces/consumer/ProductMetricsConsumerIntegrationTest.java
[ranking-e2e-test]: https://github.com/katiekim17/loop-pack-be-l2-vol3-java/blob/volume-9/apps/commerce-api/src/test/java/com/loopers/interfaces/api/RankingV1ApiE2ETest.java

🤖 Generated with [Claude Code](https://claude.ai/claude-code)
