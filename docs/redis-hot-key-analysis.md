fsd 디자인
디자인 에이전트
에고 파괴자- > 
browser mcp
엣지 애플리케이션
headless UI
하네스 엔지니어링
500kb
이미지 캐싱
이 화면에서의 브레이킹 포인트
이미지의 최고사이즈에서 가장 잘 보일 수 있는 이미지 품질 찾기
cloud front
자동화의 니즈, 어드민으로 옮겼더니 더 많은 업무, 비용이 98% 감소
기획서.
이 일이 어떤 업무 인거 같아?, 어떤 비즈니스의 임팩트가 있을까?
https://news.hada.io/topic?id=27599&utm_source=slack&utm_medium=bot&utm_campaign=T07SMH6D82Z
업무가 왜 시작됐는지부터 자세하게 세세하게 말해
어떤 이유로 됐고,, 어떤 어려움이 있었고, 
이 모든걸 이력서에 어떻게 담을 수 있을까?
업무의 시작과 끝을 자세히 설명해줘
업무의 기획참여, 업무 주도


# Redis Hot Key 분석 - Kafka Batch Consumer + ZINCRBY 구조

## 배경

Kafka Batch Consumer에서 이벤트마다 ZINCRBY를 호출하는 구조로 구현되어 있음.
인기 상품에 이벤트가 집중되면 동일 ZSET key에 write가 몰리는 hot key 문제가 발생할 수 있다고 판단함.

### 고려 중인 전환 방향

배치 내에서 상품별 delta를 메모리에 집계한 뒤 ZINCRBY를 1회만 호출하는 구조로 전환.

---

## 현재 코드 구조

### ProductMetricsConsumer (이벤트 처리)

`apps/commerce-streamer/src/main/java/com/loopers/interfaces/consumer/ProductMetricsConsumer.java`

```java
@KafkaListener(
    topics = {"product.like.events", "product.payment.events", "product.view.events"},
    groupId = "product-metrics-consumer",
    containerFactory = KafkaConfig.BATCH_LISTENER
)
public void consume(List<ConsumerRecord<Object, Object>> messages, Acknowledgment acknowledgment) {
    for (ConsumerRecord<Object, Object> message : messages) {
        // 이벤트마다 1건씩 처리 → 이벤트마다 Redis write 발생
        productMetricsService.handle(kafkaMessage);
    }
    acknowledgment.acknowledge();
}
```

**문제**: Batch Listener임에도 배치 내에서 집계 없이 이벤트마다 Redis write 발생.

---

### ProductMetricsService (점수 계산)

`apps/commerce-streamer/src/main/java/com/loopers/domain/metrics/ProductMetricsService.java`

```java
private static final double WEIGHT_VIEW = 0.1;
private static final double WEIGHT_LIKE = 0.2;
private static final double WEIGHT_SOLD = 0.6;

switch (message.eventType()) {
    case "LIKE_CREATED"  -> rankingRepository.incrementScore(payload.productId(), WEIGHT_LIKE, rankingDate);
    case "LIKE_DELETED"  -> rankingRepository.incrementScore(payload.productId(), -WEIGHT_LIKE, rankingDate);
    case "PRODUCT_SOLD"  -> rankingRepository.incrementScore(payload.productId(), WEIGHT_SOLD * Math.log1p(payload.amount()), rankingDate);
    case "PRODUCT_VIEWED"-> rankingRepository.incrementScore(payload.productId(), WEIGHT_VIEW, rankingDate);
}
```

---

### RankingRepositoryImpl (Redis ZINCRBY 호출)

`apps/commerce-streamer/src/main/java/com/loopers/infrastructure/ranking/RankingRepositoryImpl.java`

```java
private static final String KEY_PREFIX = "ranking:all:";
private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

@Override
public void incrementScore(Long productId, double score, LocalDate date) {
    String key = KEY_PREFIX + date.format(DATE_FORMAT);
    redisTemplate.opsForZSet().incrementScore(key, productId.toString(), score);  // ZINCRBY
    redisTemplate.expire(key, TTL_DAYS, TimeUnit.DAYS);
}
```

**실제 Redis 명령어:**
```
ZINCRBY ranking:all:20260409 {productId} {delta}
```

> ⚠️ `ranking:yyyyMMdd`가 아니라 `ranking:all:yyyyMMdd` 임에 주의.

---

## Hot Key 문제 분석

### Hot Key란?

Redis는 **single-threaded** 구조로 동작한다.
특정 key에 read/write 요청이 집중되면 → 해당 key를 담당하는 Redis 노드가 병목이 되는 현상.

### 현재 구조에서 Hot Key 발생 여부

```
ranking:all:20260409
    ├── 좋아요 이벤트 → ZINCRBY
    ├── 조회 이벤트  → ZINCRBY
    └── 구매 이벤트  → ZINCRBY
         (모든 상품, 모든 이벤트 타입이 이 키 하나에 집중)
```

오늘 발생하는 **모든 상품의 모든 이벤트**가 `ranking:all:20260409` 키 하나에 집중된다.
→ **Hot Key O**

---

### "키가 하나라서 Hot Key가 없다"는 주장에 대한 반론

이 주장은 틀렸다.

Hot key 여부는 **key 구조** 가 아니라 **"특정 key에 요청이 몰리는가"** 로 결정된다.
키가 하나라는 것은 "모든 트래픽이 분산 없이 한 곳에 집중"되는 구조이므로,
Hot key가 없는 게 아니라 **구조 자체가 Hot key**다.

---

### 키 구조별 Hot Key 비교

| Key 구조 | Hot Key 발생 이유 | 심각도 |
|---------|-----------------|--------|
| `ranking:all:yyyyMMdd` (현재) | 하루치 이벤트 전체가 키 1개에 집중 | **더 심각** |
| `ranking:product:{id}:yyyyMMdd` | 인기 상품 ID의 키에 이벤트 집중 | 분산되어 있어 상대적으로 낮음 |

두 구조 **모두** Hot key가 발생한다.
다만 현재 구조(`ranking:all:`)가 더 심각한 이유는, 상품 수/인기 여부와 무관하게 **모든 이벤트가 단일 키에 집중**되기 때문이다.

---

## 개선 방향: 배치 내 집계 후 ZINCRBY 1회 호출

### 핵심 아이디어

```
[현재]
이벤트 A (productId=1) → ZINCRBY ranking:all:20260409 1 0.1
이벤트 B (productId=1) → ZINCRBY ranking:all:20260409 1 0.1
이벤트 C (productId=1) → ZINCRBY ranking:all:20260409 1 0.2
이벤트 D (productId=2) → ZINCRBY ranking:all:20260409 2 0.6
→ Redis write 4회

[개선]
배치 내 메모리 집계:
  productId=1 → delta 0.4
  productId=2 → delta 0.6

ZINCRBY ranking:all:20260409 1 0.4
ZINCRBY ranking:all:20260409 2 0.6
→ Redis write 2회 (상품 종류 수로 감소)
```

### 효과

- Redis write 횟수 감소 → 단일 키에 대한 write 부하 완화
- 단순 Redis write 수 감소 외에도, 네트워크 round-trip 감소로 처리량(throughput) 향상

### Hot Key의 본질적인 완화 방법

Key 구조 변경이 아니라, **Redis로 가는 write 횟수 자체를 줄이는 것**이 핵심이다.

---

## 남은 논의 포인트

- 단순 Redis write 수 감소 외에, 이 구조 전환이 필요한 **운영상 신호**가 무엇인지
- 전환 시 dual write나 shadow 방식의 **점진적 전환**이 일반적인지
- 배치 집계 단위(1초, 5초, 10초)를 결정할 때 **latency와 throughput 사이에서 어떤 기준**을 쓰는지
