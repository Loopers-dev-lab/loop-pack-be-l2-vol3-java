# Redis ZSET 랭킹 — Hot Key / 처리량 / 장애 복구 분석

랭킹 기능 구현 후 진행한 아키텍처 Q&A를 정리한 문서. 코드 변경이 아닌 **설계 판단의 근거**를 남기는 용도.

---

## 1. ZADD vs ZINCRBY — 멱등성과 race condition

### 잘못된 발상

> "DELTA를 메모리에서 집계해서 ZADD하면 효율적이지 않을까?"

이 표현은 두 가지 측면에서 부정확하다.

1. **ZADD는 set이고 ZINCRBY는 add다.** ZADD로 같은 멤버에 점수를 다시 쓰면 누적이 아니라 **덮어쓰기**가 된다.
2. 멀티 인스턴스 환경에서 각 인스턴스가 자기 메모리의 DELTA를 ZADD로 쓰면, 서로 마지막 write가 앞 write를 덮어버리는 **race condition**이 생긴다.

### 올바른 패턴

**메모리에서 집계 → ZINCRBY**.
ZINCRBY는 원자적 read-modify-write이므로 멀티 인스턴스에서도 안전하다.

```
[잘못] DELTA 집계 후 ZADD  → race condition (lost update)
[옳음] DELTA 집계 후 ZINCRBY → 원자적 누적, 안전
```

### 멱등성은 어디서 보장되나

ZINCRBY는 그 자체로 멱등하지 않다 (호출할 때마다 누적). 멱등성은 **EventHandled 테이블의 eventId 유니크 제약**에서 온다.

```
같은 eventId 메시지가 2번 들어오면
  → EventHandled INSERT 시점에 unique 위반
  → 두 번째는 skip
  → ZINCRBY는 한 번만 호출됨
```

즉 우리 구조는 "ZINCRBY 자체의 멱등성"이 아니라 "EventHandled가 중복을 막아주니 ZINCRBY가 한 번만 실행됨"으로 정확성을 담보한다.

---

## 2. Hot Key는 진짜 문제인가

### 흔한 오해

> "인기 상품에 이벤트가 집중되면 동일 ZSET key에 write가 몰리는 hot key 문제가 발생한다."

### 정정

ZSET key는 `ranking:all:{yyyyMMdd}` **하나**다. 인기 상품이든 비인기 상품이든 모두 같은 key에 들어간다. 인기 상품 1개가 따로 hot key를 만드는 게 아니라 — 애초에 모든 상품이 하나의 key를 공유한다.

각 productId는 ZSET 내부에서 **다른 member**일 뿐이다. Redis 입장에서 ZINCRBY는 동일 key에 대한 단일 원자 연산이므로:

- 서로 다른 productId의 ZINCRBY는 동일 key를 건드려도 충돌하지 않음
- "인기 상품 = hot member"는 성능 이슈가 아님
- Redis 단일 노드는 ZINCRBY를 초당 수십만 건 처리 가능

### 그럼 hot key는 언제 진짜 문제인가

- key를 sharding한 클러스터에서 **특정 슬롯 노드에 트래픽이 집중**될 때
- 특정 key 하나의 RPS가 **단일 노드 처리량을 초과**할 때

우리 규모(단일 Redis, 일별 key)에서는 해당 없음.

### 결론

> "랭킹은 ZSET 1개로 충분하고 hot key 회피용 sharding이 필요 없다"는 게 우리 시스템의 정확한 진단.

---

## 3. 처리량 최적화 — round-trip이 진짜 병목

### "Redis round-trip 3,000번"이 무슨 뜻인가

3,000개 메시지를 단건 처리한다고 가정하면:

```
for (msg in 3000개):
    redis.zincrby(key, productId, score)   ← TCP 왕복 1회
```

각 ZINCRBY는 `commerce-streamer → Redis → commerce-streamer` 네트워크 왕복을 발생시킨다. 메시지 1건당 RTT 1회 = **3,000번의 네트워크 왕복**. RTT가 0.5ms라면 순수 대기시간만 1.5초.

Redis 자체의 처리 속도가 빠른 건 맞지만, **네트워크 왕복 횟수가 throughput의 상한**이 된다.

### 메모리 집계로 round-trip을 줄이기

```
for (msg in 3000개):
    delta[productId] += score        ← JVM 메모리에서 누적
flush() → for (productId in unique 50개):
    redis.zincrby(key, productId, delta[productId])
```

3,000개 이벤트가 50개 unique 상품에 분산된다면:

| 방식 | Redis 명령 수 | 네트워크 왕복 |
|------|--------------|--------------|
| 단건 ZINCRBY | 3,000 | 3,000 |
| 메모리 집계 | 50 | 50 |
| 메모리 집계 + Pipeline | 50 | **1~3** |

### "메모리"는 어디인가 — 멀티 인스턴스에서 동작하는 이유

처음엔 "여러 streamer 인스턴스가 각자 메모리에 집계하면 결국 Redis로 보낼 때 합쳐지지 않을 텐데?"라고 의심할 수 있다.

답은 **Kafka 파티션 키**에 있다.

```
PaymentFacade.java:71  → producer가 productId를 partition key로 발행
LikeService.java:55    → 동일
```

Kafka는 같은 partition key를 항상 같은 partition으로, 같은 partition은 항상 같은 consumer group instance로 보낸다. 따라서:

```
productId=42 이벤트는 항상 streamer-instance-A로만 감
productId=99 이벤트는 항상 streamer-instance-B로만 감
```

→ 메모리 집계는 인스턴스별로 분리되지만, **같은 productId가 인스턴스를 가로지르지 않음**. ZINCRBY로 합쳐지는 시점에서도 충돌이 없다.

### Redis Pipeline — 또 다른 차원의 최적화

50개 ZINCRBY를 단건 호출하면 여전히 50번의 RTT. 이를 Pipeline으로 묶으면 **명령은 50개지만 네트워크 왕복은 1~3회**로 줄어든다.

```
pipeline.zincrby(key, p1, s1)
pipeline.zincrby(key, p2, s2)
... (50개 적재)
pipeline.sync()  ← 한 번에 전송, 한 번에 응답 수신
```

Pipeline은 트랜잭션이 아니라 단순 batching. 명령 사이 원자성은 보장되지 않지만 ZINCRBY는 각각이 원자적이라 문제 없다.

### Kafka ack 시점은 언제가 맞는가

랭킹 flush 배치에서는 **Redis flush가 성공한 뒤에 ack**하는 것이 맞다.

```
1. Kafka batch 수신
2. 메시지별 점수 계산
3. 메모리 delta 집계
4. Redis flush (ZINCRBY batch + pipeline)
5. flush 성공 후 ack
```

이 순서를 택하는 이유는 분명하다.

- **ack를 먼저 하면 유실 가능성**이 생긴다.
- **flush를 먼저 하면 중복 가능성**만 남는다.

예를 들어 ack 후 flush 전에 프로세스가 죽으면, Kafka는 이미 처리 완료로 간주하므로 Redis 점수는 영구 유실된다. 반대로 flush 성공 후 ack 전에 죽으면 Kafka가 메시지를 다시 전달할 수는 있지만, 이 경우는 **중복 방지 장치로 제어 가능한 문제**다.

우리 구조에서는 이미:

- Kafka 재전달 가능성을 전제하고 있고
- outbox 패턴으로 발행 유실 가능성을 낮추고 있으며
- `EventHandled` 의 eventId 유니크 제약으로 중복 반영을 막는 방향을 택하고 있다

따라서 flush 배치 계층의 원칙도 동일하다.

> **유실보다 중복 가능성을 택한다.**
> 그러므로 배치 컨슈머의 ack 시점은 반드시 `flush -> ack` 순서여야 한다.

### flush 실패 시 동작

flush 실패 시에는 ack 하지 않고 배치를 재처리 경로로 넘기는 단순한 전략이 맞다.

```
flush 성공  -> ack
flush 실패  -> no ack / 예외 / 재처리
```

별도 내부 재시도 큐를 두는 방식도 가능하지만, 현재 구조는 이미 Kafka + outbox + 중복 방지 장치가 있으므로 flush 계층까지 복잡한 재시도 상태를 들고 갈 이유가 크지 않다.

### 메모리 집계 자료구조는 어떻게 잡는가

flush 배치의 메모리 집계는 결국 **`(rankingDate, productId) -> delta`** 를 만드는 과정이다.

가장 자연스러운 자료구조는 아래와 같다.

```java
Map<LocalDate, Map<Long, Double>> deltaByDateAndProduct;
```

의미는 다음과 같다.

```text
2026-04-09
  product 42 -> +1.3
  product 99 -> +0.4

2026-04-10
  product 42 -> +0.1
```

이 구조를 쓰는 이유는:

- 랭킹 저장소가 **일별 ZSET key** (`ranking:all:yyyyMMdd`) 이고
- 같은 productId라도 **날짜가 다르면 다른 Redis key** 로 들어가야 하며
- 같은 Kafka batch 안에도 **자정 경계 이벤트가 섞일 수 있기 때문**이다.

즉 `Map<Long, Double>` 하나로 끝내면 안 되고, 날짜 차원을 반드시 함께 들고 가야 한다.

### 집계 로직은 어떻게 생기나

메시지 1건을 처리할 때 하는 일은 단순하다.

1. `occurredAt` 에서 `rankingDate` 를 구한다.
2. 이벤트 타입에 따라 점수 delta 를 계산한다.
3. 메모리 맵에 `merge(..., Double::sum)` 으로 누적한다.

```java
LocalDate rankingDate = occurredAt.atZone(zoneId).toLocalDate();
double delta = scoreOf(message);

deltaByDateAndProduct
    .computeIfAbsent(rankingDate, ignored -> new HashMap<>())
    .merge(productId, delta, Double::sum);
```

예를 들어 같은 batch 안에 아래 이벤트가 들어오면:

```text
LIKE_CREATED product=42   -> +0.2
PRODUCT_VIEWED product=42 -> +0.1
PRODUCT_VIEWED product=42 -> +0.1
PRODUCT_SOLD product=99   -> +1.8
```

메모리 집계 결과는:

```text
42 -> +0.4
99 -> +1.8
```

즉 **이벤트 수만큼 Redis에 쓰지 않고, unique product 수만큼만 flush** 하게 된다.

### 왜 이 구조가 flush 와 잘 맞는가

flush 단계에서는 날짜별로 나눠 순회하면 된다.

```java
for (Map.Entry<LocalDate, Map<Long, Double>> entry : deltaByDateAndProduct.entrySet()) {
    LocalDate date = entry.getKey();
    Map<Long, Double> productDeltas = entry.getValue();

    // date 별 Redis key 1개에 대해
    // product 별 ZINCRBY 를 pipeline 으로 전송
}
```

즉 자료구조 자체가 flush 단위를 그대로 표현한다.

### 설계상 주의점

- `occurredAt -> LocalDate` 변환에 사용할 **timezone 을 고정**해야 한다.
- batch 크기보다 중요한 것은 **unique product 수**다. 메모리 사용량은 보통 메시지 수보다 unique key 수에 비례한다.
- 점수 자료형은 현재 구조에선 `double` 로 충분하지만, 장기적으로 정밀도 요구가 커지면 별도 검토가 필요하다.

---

## 4. SoT (Source of Truth)와 장애 복구

### Redis가 통째로 날아가면 ZSET은 어떻게 복구되나

이 질문이 Redis vs DB 논의의 핵심이다.

처음엔 "Redis 앞에 DB를 두고 동기적으로 저장하면 복구가 가능하지 않나?"라는 발상을 할 수 있다. 하지만:

1. **DB 쓰기는 Redis보다 10~50배 비싸다** (디스크 I/O + WAL fsync)
2. 우리는 **이미 DB에 쓰고 있다** — `product_metrics` 테이블에 view/like/sold count가 누적됨
3. 그런데 `product_metrics`는 **카운트만** 저장, ZSET 점수(`0.6 × log1p(amount)`)를 정확히 복구할 수는 없음

### 진짜 SoT는 누구인가

```
[commerce-api]
   ↓ 결제/좋아요 트랜잭션과 원자적 INSERT
outbox_event 테이블 (SoT 1)
   ↓ OutboxRelay polling
Kafka topic (SoT 2)
   ↓ consumer
[commerce-streamer]
   ├─→ product_metrics (파생 집계 1)
   └─→ Redis ZSET (파생 집계 2)
```

Redis는 **파생 캐시**일 뿐, source of truth는 **outbox_event 테이블 + Kafka topic** 두 겹.

### 복구 시나리오

| 장애 | 복구 방법 |
|------|----------|
| Redis 통째 손실 | Kafka consumer group offset reset → 처음부터 재컨슘 → ZSET 자동 재구성 |
| Kafka retention 만료 | outbox_event 테이블에서 republish |
| outbox_event 테이블 손실 | 결제/좋아요 트랜잭션과 묶여 있어 발생 불가 |

→ Redis 앞에 DB를 동기적으로 끼워넣을 필요가 **전혀 없다**. 이미 더 강한 SoT 두 겹이 앞단에 있다.

---

## 5. outbox 보호 범위 — 가중치 vs 비용 트레이드오프

### 모든 이벤트가 outbox를 거치는가? 아니다.

| 이벤트 | 가중치 | 발행 위치 | outbox 사용 |
|--------|-------|----------|------------|
| `PRODUCT_SOLD` | 0.6 × log1p(amount) | `PaymentFacade.java:68` | ✅ |
| `LIKE_CREATED` | +0.2 | `LikeService.java:51` | ✅ |
| `LIKE_DELETED` | -0.2 | `LikeService.java:77` | ✅ |
| `PRODUCT_VIEWED` | +0.1 | `ProductsV1Controller.java:71` | ❌ KafkaTemplate 직접 |

### 의도된 트레이드오프

PRODUCT_VIEWED는 outbox를 안 쓴다. 이유:

- 트래픽 압도적 다수 (모든 상품 페이지 뷰 = 이벤트 1건)
- 매 view마다 DB INSERT를 동반하면 read 응답시간 증가
- view 한두 건 손실은 비즈니스 임팩트 없음
- 발행 실패 시 try-catch로 warn 로그만 남기고 정상 응답 (`ProductsV1Controller.java:74`)

### 결과적 설계 원칙

> **가중치가 큰 이벤트일수록 강한 일관성(outbox), 가벼운 이벤트는 best-effort.**
> 처리량과 정확성을 이벤트 가치에 따라 차등 적용.

ZSET 점수 기여도 기준으로 보면:

```
보호됨   : sold(0.6 × log1p(amount)) + like(0.2)  ← 점수의 큰 비중
best-effort : view(0.1)                           ← 점수의 작은 비중
```

---

## 6. 캐시와 실시간 데이터의 분리

`ProductFacade.getProductDetail()`은 5분 TTL의 Redis 캐시를 사용한다. 만약 ranking 필드를 이 캐시에 포함시키면:

- 새 주문이 들어와 ZSET 점수가 바뀌어도 5분간 이전 순위가 반환됨
- 캐시 invalidation을 ranking 변경마다 트리거할 수도 없음 (ranking은 모든 이벤트마다 변함)

### 해결: 캐시 바깥에서 별도 조회

```java
// ProductsV1Controller.java
Integer ranking = rankingRepository.getRank(productId, LocalDate.now()).orElse(null);
return ApiResponse.success(
    ProductV1Dto.ProductDetailResponse.from(
        productFacade.getProductDetail(productId),  // 캐시 히트
        ranking                                      // 매번 live 조회
    )
);
```

- 상품 메타데이터(이름, 가격, 설명) → 자주 안 바뀜 → 캐시
- ranking → 매 이벤트마다 바뀜 → live Redis 조회

두 관심사를 캐시 정책 단위로 분리.

---

## 7. 책임 분리 — 랭킹 전처리는 어디에 있는가

### 원칙

> "랭킹에 필요한 형태로 데이터를 가공하는 책임은 온전히 랭킹 시스템 내부의 전처리 레이어가 담당해야 한다."

**발행 측은 도메인 사실(fact)만 던지고, 랭킹 시스템이 그 사실을 점수로 변환한다.**
이렇게 분리하면 발행 측은 랭킹의 존재 자체를 몰라도 되고, 가중치 정책을 바꿀 때 랭킹 코드 한 곳만 수정하면 된다.

### 우리 코드에서 전처리 레이어 = `ProductMetricsService.handle()`

`apps/commerce-streamer/src/main/java/com/loopers/domain/metrics/ProductMetricsService.java:32-70`

```
[발행 측 — 비즈니스 도메인]                    [전처리 레이어 — 랭킹 시스템]
PaymentFacade.java:68                          ProductMetricsService.handle()
   "주문 발생, amount=20000"          ──→         WEIGHT_SOLD × log1p(amount)
                                                 = 0.6 × log1p(20000) ≈ 5.94

LikeService.java:51                            "좋아요 발생, productId=42"
                                       ──→         +WEIGHT_LIKE = +0.2

ProductsV1Controller.java:71                   "상품 조회됨"
                                       ──→         +WEIGHT_VIEW = +0.1
```

### 각 줄이 어떤 변환을 하는가

| 줄 | 변환 |
|----|------|
| `ProductMetricsService.java:23-25` | 가중치 상수 정의 (`0.1`, `0.2`, `0.6`) |
| `ProductMetricsService.java:42` | `occurredAt` → `LocalDate rankingDate` (날짜별 ZSET key 결정) |
| `ProductMetricsService.java:48` | LIKE_CREATED → `+0.2` |
| `ProductMetricsService.java:53` | LIKE_DELETED → `-0.2` (취소는 음수 가산) |
| `ProductMetricsService.java:58` | PRODUCT_SOLD → `0.6 × log1p(amount)` (정규화) |
| `ProductMetricsService.java:64` | PRODUCT_VIEWED → `+0.1` |

### 분리가 가져다주는 효과

**`PaymentFacade`는 "랭킹"이라는 단어를 모른다.** 그냥 "주문이 결제됐고 금액은 20000원이다"라는 사실만 outbox로 발행한다.

**`LikeService`도 마찬가지.** "좋아요 1건 발생"만 발행할 뿐, 이게 ZSET 점수에서 +0.2인지 +0.5인지 모른다.

**가중치를 바꾸고 싶을 때 (예: log1p → log10, 0.6 → 0.8) 발행 측 코드는 한 줄도 수정 안 해도 된다.** `ProductMetricsService`의 상수만 바꾸면 끝.

```java
// 가중치 정책 변경 시 — 여기만 수정
private static final double WEIGHT_VIEW = 0.1;   // → 0.05로 바꾸면?
private static final double WEIGHT_LIKE = 0.2;
private static final double WEIGHT_SOLD = 0.6;
```

→ `PaymentFacade`, `LikeService`, `ProductsV1Controller` 어디도 안 건드림. **랭킹 정책이 랭킹 시스템 안에 캡슐화**되어 있다는 증거.

### 미묘한 예외 — `amount`는 발행 측에서 계산된다

`PaymentFacade.java`에서 `itemAmount = price × quantity`를 계산해서 payload에 담는다. 이건 엄밀히 말하면 "랭킹용 전처리"가 아니라 **"이벤트가 포함해야 할 비즈니스 사실"**이다.

가격과 수량은 결제 시점에만 알 수 있는 정보고, 나중에 streamer에서 다시 조회하려면 product 테이블 lookup이 또 필요하기 때문에 발행 시점에 함께 직렬화하는 것이다.

`log1p`나 `0.6`을 PaymentFacade에서 적용했다면 원칙 위반이지만, **raw amount만 전달하고 변환은 streamer에서** 하므로 책임 분리는 깨끗하게 유지된다.

### 3단 분리 구조 정리

| 레이어 | 책임 | 코드 위치 |
|--------|------|----------|
| **발행 측** | "무엇이 일어났는가" (도메인 사실) | `PaymentFacade`, `LikeService`, `ProductsV1Controller` |
| **전처리 레이어** | "그것이 랭킹에서 얼마의 가치를 가지는가" (정책) | `ProductMetricsService.handle()` |
| **저장 레이어** | "그 점수를 어떻게 ZSET에 반영하는가" (저장 방식) | `RankingRepositoryImpl.incrementScore()` |

이 3단 분리 덕분에 발행 측이 랭킹 시스템의 존재 자체를 몰라도 동작한다. 내일 랭킹을 없애고 싶어도 `commerce-streamer`만 수정하면 끝.

---

## 8. 멱등성 — 랭킹 신뢰도의 최후 방어선

### 원칙

> "이벤트 기반 시스템에서 '두 번 처리되면 어떡하지?'라는 질문은 피할 수 없는 숙제다.
> Kafka의 exactly-once는 프로듀서의 예상치 못한 오류에만 한정되며, 애플리케이션이 명시적으로
> 같은 메시지를 두 번 보내면 Kafka 입장에서는 서로 다른 메시지로 인식된다.
> 따라서 데이터 전처리 레이어가 멱등성을 보장하는 최후의 방어선 역할을 해야 한다."

랭킹 신뢰도는 곧 매출 신뢰도다. 어제 10번 팔린 상품이 시스템 오류로 20번 팔린 것처럼 집계되어
1위로 올라간다면 누구도 그 랭킹을 믿지 않는다.

### 우리 코드의 방어선 — 결론부터

| 이벤트 | eventId 생성 위치 | 멱등성 보장 |
|--------|-----------------|------------|
| `PRODUCT_SOLD` | `OutboxEvent.java:49` — outbox row 생성 시 UUID 1회 | ✅ 견고 |
| `LIKE_CREATED/DELETED` | 동일 (outbox row UUID) | ✅ 견고 |
| `PRODUCT_VIEWED` | `ProductsV1Controller.java:66` — **HTTP 요청마다** 새 UUID | ❌ 사실상 무방비 |

**3개 중 2개는 방어선이 잘 작동하고, 1개는 의도적으로 비워둔 구멍이 있다.**

### 1) outbox 경유 이벤트 — 방어선이 진짜로 작동하는 이유

#### eventId의 안정성

`OutboxEvent.create()` 시점에 **DB row 1개에 UUID 1개**가 박힌다 (`OutboxEvent.java:49`).
이 row는 결제 트랜잭션과 원자적으로 commit된다.

- 결제 트랜잭션 롤백 → outbox row 자체가 없음 → 이벤트도 없음
- 결제 트랜잭션 성공 → outbox row와 UUID가 영구 보존
- OutboxRelay가 같은 row를 100번 재발행해도 → 100번 모두 **같은 eventId**

#### EventHandled의 차단 동작

`ProductMetricsService.java:33-36`:

```java
if (eventHandledRepository.existsByEventId(message.eventId())) {
    log.info("중복 이벤트 스킵. eventId={}, eventType={}", ...);
    return;
}
```

`event_handled.event_id` 컬럼은 unique constraint (`EventHandled.java:10`).
**두 가지 방어선이 겹쳐 있다**:

1. **읽기 시점 체크** (existsByEventId) — 정상 케이스에서 빠른 차단
2. **쓰기 시점 unique constraint** — 동시성 race를 잡는 최후의 보루

→ PRODUCT_SOLD와 LIKE는 위 원칙이 정확히 작동한다.

### 2) PRODUCT_VIEWED — 방어선이 뚫려있는 이유

`ProductsV1Controller.java:62-71`:

```java
String userId = (loginId != null) ? loginId : "unknown";
KafkaOutboxMessage message = new KafkaOutboxMessage(
    UUID.randomUUID().toString(),  // ← 매 HTTP 요청마다 새 UUID
    "PRODUCT_VIEWED",
    payloadJson,
    ...
);
```

같은 사용자가 새로고침을 10번 하면:
- 10개의 서로 다른 UUID
- EventHandled 입장에서는 모두 "처음 보는 이벤트"
- 10번 모두 ZSET에 +0.1 누적

원칙 인용문에 빗대어 표현하면:

> 어제 100명이 본 상품이 봇 새로고침으로 1000번 본 것처럼 집계되어 랭킹 1위로 올라간다면…

**이건 시스템 오류가 아니라 정상 동작으로도 발생한다.** 봇/크롤러/사용자 새로고침 모두 이 구멍을 통과한다.

#### 왜 이렇게 설계됐는가 — 의도된 트레이드오프

- view는 가중치 0.1로 가장 작음 → 오염되어도 영향 작다는 가정
- view는 outbox를 안 거치므로 DB row가 없음 → eventId의 "natural key"가 없음
- 매 요청 UUID가 가장 단순한 구현

#### 만약 막고 싶다면 — 두 가지 대안

**대안 A**: 단시간 dedup key (대기열 presence 패턴과 동일)

```java
String dedupKey = "view:dedup:" + userId + ":" + productId;
Boolean isNew = redisTemplate.opsForValue()
    .setIfAbsent(dedupKey, "1", Duration.ofHours(1));
if (Boolean.TRUE.equals(isNew)) {
    rankingRepository.incrementScore(productId, WEIGHT_VIEW, today);
}
```

**대안 B**: eventId를 결정론적으로 생성

```java
// HTTP 요청마다 다른 UUID 대신
String eventId = "view:" + userId + ":" + productId + ":" + LocalDate.now();
```

같은 사용자가 같은 날 같은 상품을 100번 봐도 → eventId가 동일 →
**EventHandled가 자동으로 중복 차단**. 추가 코드 없이 기존 방어선을 활용 가능.

### 3) 발견한 미세한 race condition — Redis는 트랜잭션 밖

`ProductMetricsService.java:20`에 `@Transactional`이 걸려있다. 흐름을 보면:

```
1. existsByEventId 체크                  (DB 읽기)
2. productMetricsRepository.increment... (DB 쓰기, 트랜잭션 안)
3. rankingRepository.incrementScore      (Redis 쓰기, 트랜잭션 밖)
4. eventHandledRepository.save           (DB 쓰기, 트랜잭션 안)
```

만약 4번에서 unique constraint 위반이 터지면:
- 트랜잭션 롤백 → DB는 깨끗
- **하지만 Redis ZSET은 이미 점수가 오염된 상태**

#### 발생 조건

- 같은 eventId 메시지를 두 컨슈머가 거의 동시에 처리
- 둘 다 1번 체크 통과 (아직 EventHandled에 없음)
- 둘 다 3번 ZINCRBY 실행 → ZSET +0.2 두 번 (오염)
- 4번에서 한 쪽만 성공, 다른 쪽 unique 위반 → DB는 한 건만, Redis는 두 건

Kafka partition key 라우팅 덕분에 같은 productId 메시지가 같은 컨슈머로만 가서 평소엔 발생 안 한다.
**컨슈머 리밸런싱 중에는** 두 인스턴스가 잠깐 동시 처리할 수 있어서 이론적 가능성은 존재.

#### 더 견고하게 하려면 — EventHandled INSERT를 ZINCRBY 앞으로

```java
// 개선안
if (existsByEventId(eventId)) return;
try {
    eventHandledRepository.save(new EventHandled(eventId));  // 먼저 차단
    // unique 위반 시 여기서 throw → ZINCRBY 안 감
} catch (DataIntegrityViolationException e) {
    return;  // 동시 처리 충돌 — 다른 쪽에 양보
}
productMetricsRepository.increment...
rankingRepository.incrementScore(...);
```

이렇게 하면 ZINCRBY는 **eventId가 정확히 한 번만 통과한 흐름에서만** 실행된다.
단, "EventHandled save 성공 후 ZINCRBY 실패" 케이스에서는 이벤트가 영구 누락되므로
DLQ나 별도 보정 잡이 필요하다 — 즉 "유실 vs 중복" 트레이드오프가 다시 등장.

현재 코드는 "**flush → ack** 순서를 택해 유실보다 중복을 허용"하는 섹션 3의 원칙과 일치한다.
중복은 EventHandled가 99% 잡아주고, 리밸런싱 같은 1% 케이스에서는 미세한 over-count를 허용.

### 정리 — 우리 코드의 방어선 평가

| 항목 | 상태 |
|------|------|
| outbox 경유 이벤트(sold/like)의 멱등성 | ✅ 견고 — 원칙이 정확히 작동 |
| view 이벤트의 멱등성 | ❌ 사실상 없음 — eventId가 매 요청 새로 생김 |
| 동시 처리 race (리밸런싱 등) | ⚠️ 미세한 구멍 — Redis가 트랜잭션 밖이라 EventHandled가 ZINCRBY를 못 막음 |
| 가중치별 위험도 | sold(0.6 × log1p) / like(0.2)는 보호됨, view(0.1)는 노출됨 |

**핵심**: "비워둔 구멍"과 "구멍이 있는 줄 모르는 것"은 다르다.
view 이벤트의 멱등성 부재는 의도된 트레이드오프지만, 이 분석 문서에 명시적으로 인지된 결함으로 기록한다.
필요해지면 대안 B (결정론적 eventId)가 가장 적은 코드 변경으로 적용 가능.

---

## 9. 정리 — 자랑할만한 설계 포인트

1. **ZINCRBY vs ZADD 차이를 인지**하고 race condition 없는 패턴 선택
2. **Hot key 오해 정정** — ZSET 단일 key는 우리 규모에서 문제가 아님
3. **Round-trip이 진짜 병목**임을 식별 → 메모리 집계 + Pipeline로 throughput 확보
4. **메모리 집계가 멀티 인스턴스에서 동작하는 이유**가 Kafka partition key 라우팅에 있음을 이해
5. **Kafka ack 시점은 flush → ack** — 유실보다 중복을 택하고, 중복은 멱등성으로 차단
6. **Redis는 파생 캐시, outbox + Kafka가 진짜 SoT** — Redis 앞에 DB를 끼우는 발상이 불필요한 이유
7. **이벤트 가중치별 차등 일관성** — sold/like는 outbox 보호, view는 best-effort
8. **랭킹 전처리 책임이 랭킹 시스템 내부에 캡슐화** — 발행 측은 도메인 사실만 던지고 가중치/정규화는 `ProductMetricsService`가 전담
9. **멱등성 방어선의 강·약점을 모두 인지** — sold/like는 EventHandled로 견고히 보호되고, view의 구멍은 의도된 트레이드오프로 명시
10. **캐시와 실시간 데이터 분리** — productDetail 캐시는 유지하면서 ranking만 live 조회
