# 상품 랭킹 시스템

## 1. 왜 랭킹인가 — RDB의 한계

### 문제

상품이 100만 건이고, 유저가 홈 화면에 진입할 때마다 인기 상품 Top 10을 조회한다고 하자.

```sql
SELECT product_id, COUNT(*) as order_count
FROM order_items
GROUP BY product_id
ORDER BY order_count DESC
LIMIT 10;
```

초기에는 빠르다. 하지만 주문이 수백만 건 쌓이면?

- `GROUP BY + ORDER BY`는 전체 테이블 스캔
- 인덱스를 써도 집계 연산은 피할 수 없음
- 조회 빈도가 높을수록 DB 부하가 선형으로 증가

결국 **캐시를 쓴다**. 그런데 일반 캐시(`String`)는 "10분마다 갱신"처럼 주기적으로 전체를 다시 계산해야 한다. 이벤트가 발생할 때마다 실시간으로 랭킹을 반영할 수 없다.

| 방법 | 장점 | 단점 | 적합도 |
|---|---|---|---|
| DB `ORDER BY` | 정합성 높음 | 느림, 부하 높음 | 초기/소규모 |
| 캐시(Map) + 정렬 | 구현 단순 | 매 요청마다 정렬 필요 | 중간 규모 |
| Redis ZSET | 정렬 내장, 실시간 반영 | 메모리 사용 | 대규모 트래픽 |

---

## 2. Redis Sorted Set (ZSET)

### 구조

ZSET은 `(member, score)` 쌍을 score 기준으로 **항상 정렬된 상태**로 유지한다.

```
ranking:all:20260408
  └── product:101 → 87.3
  └── product:205 → 54.1
  └── product:312 → 31.7
```

- 삽입/수정: O(log N)
- Top-N 조회: O(N)

### 주요 연산

```
ZINCRBY  ranking:all:20260408 0.1 "101"     // 상품 101 점수 += 0.1
ZREVRANGE ranking:all:20260408 0 9 WITHSCORES  // Top 10 조회 (score 높은 순)
ZREVRANK  ranking:all:20260408 "101"        // 상품 101의 순위 (0-based)
ZSCORE    ranking:all:20260408 "101"        // 상품 101의 현재 점수
ZCARD     ranking:all:20260408              // 전체 멤버 수
```

---

## 3. Key 설계 — 시간의 양자화

### 단순 누적의 문제

하나의 Key에 계속 점수를 쌓으면:

- 서비스 초기부터 인기 있던 상품이 영원히 상위를 독식
- 신상품은 점수를 따라잡을 기회가 없음
- 결국 **롱테일 현상** — 소수 상품이 랭킹 상위를 고정 점유

### 해결: 날짜별 Key 분리

```
ranking:all:20260407   // 어제 랭킹
ranking:all:20260408   // 오늘 랭킹
```

- 날짜가 바뀌면 새 Key에서 0부터 시작
- 오늘 점수만 오늘 랭킹에 반영 → **공정성 확보**
- TTL 2일 설정 → 자동 만료로 메모리 관리

> TTL은 집계 윈도우(1일)의 1.5~2배로 잡는 것이 안정적이다. 전날 랭킹 조회가 필요한 경우를 커버하면서도 메모리를 낭비하지 않는다.

---

## 4. 가중치 합산 (Weighted Sum)

### 왜 가중치가 필요한가

이벤트 종류마다 의미와 스케일이 다르다.

- 조회는 클릭 한 번이지만 주문은 구매 결정이다
- 가중치 없이 단순 합산하면 조회 수가 압도적으로 많아 다른 지표가 묻힌다

### 점수 공식

```
score(product) =
    0.1 * view_count
  + 0.2 * like_count
  + 0.7 * Σ log1p(quantity)   // 주문별 수량 합산
```

| 이벤트 | Weight | Score | 이유 |
|---|---|---|---|
| 조회 | 0.1 | 1 | 빈도가 가장 많아 낮게 설정 |
| 좋아요 | 0.2 | 1 | 구매보다 낮은 의사결정 강도 |
| 주문 | 0.7 | log1p(quantity) | 구매 결정으로 가장 중요 |

### log1p를 쓰는 이유

`log1p(x) = log(1 + x)` — 자연로그(밑 e = 2.718...)에서 `1 + x`에 적용한 값이다.

**왜 1을 더하나:** `log(0) = -∞` 라서 수량이 0이면 계산이 터진다. `log(1 + 0) = log(1) = 0` 으로 안전하게 처리하기 위해서다.

**수량별 값:**

```
수량(x)   log1p(x)
──────────────────
1        0.69
2        1.10
5        1.79
10       2.40
50       3.93
100      4.62
1000     6.91
```

수량이 100배 늘어도 점수는 6.7배만 늘어난다.

**quantity를 그대로 쓰면:**

```
1개 주문   → 0.7 * 1   = 0.70
100개 주문 → 0.7 * 100 = 70    ← 100배 차이
```

대량 주문 1건이 소량 주문 수십 건을 압도해 다양한 상품이 랭킹에 오를 기회가 없어진다.

**log1p를 쓰면:**

```
1개 주문   → 0.7 * 0.69 = 0.48
100개 주문 → 0.7 * 4.62 = 3.23  ← 6.7배 차이
```

대량 주문의 영향은 인정하되, 랭킹을 지배하지는 못하게 압축한다.

**Java 코드:** `Math.log1p(quantity)` (java.lang.Math에 내장)

### ZSET 반영 방식

이벤트 발생 시 매번 전체 score를 재계산하지 않는다. `ZINCRBY`로 **증분만 더한다**.

```
조회 이벤트  → ZINCRBY ranking:all:20260408 0.1  "productId"
좋아요 이벤트 → ZINCRBY ranking:all:20260408 0.2  "productId"
주문 이벤트  → ZINCRBY ranking:all:20260408 {0.7 * log1p(quantity)} "productId"
```

---

## 5. 콜드 스타트 문제

### 문제

자정에 날짜가 바뀌면 새 Key가 생기고 모든 점수는 0이다.

```
00:00:01 → ranking:all:20260408 생성 (모든 score = 0)
```

이 시간대에 유저가 랭킹을 요청하면 결과가 없거나, 방금 이벤트 1~2건만 반영된 무의미한 랭킹이 나온다.

```
[문제 상황]
00:00 자정 → ranking:all:20260408 시작
00:00~06:00 → 트래픽 적음, 이벤트 거의 없음
06:00 출근 시간대 유저 유입
  → 랭킹 API 조회
  → 상품 2~3개만 있는 랭킹 반환
```

### 해결: Score Carry-Over

23:50에 스케줄러를 실행해 전날 점수의 10%를 새 Key에 미리 복사한다.

```
ZUNIONSTORE ranking:all:20260408 1 ranking:all:20260407 WEIGHTS 0.1

결과:
  ranking:all:20260407: product:101 → 100점
  ranking:all:20260408: product:101 → 10점  (10% carry-over)
```

자정 이후 이벤트가 쌓이면서 오늘 점수가 자연스럽게 carry-over 점수를 추월한다.

> carry-over 비율(10%)은 전날 인기 상품이 오늘 랭킹을 장기간 지배하지 않도록 의도적으로 낮게 설정한다.

---

## 6. 전체 아키텍처

```
[commerce-api]
  유저 행동 → 도메인 이벤트 발행
    ProductViewedEvent  →  Outbox  →  catalog-events  →  ZINCRBY 0.1
    ProductLikedEvent   →  Outbox  →  catalog-events  →  ZINCRBY 0.2
    ProductUnlikedEvent →  Outbox  →  catalog-events  →  ZINCRBY -0.2
    OrderConfirmedEvent →  Outbox  →  order-events    →  ZINCRBY 0.7 * log1p(quantity)

[commerce-streamer]
  Kafka Consumer (배치 리스너)
    → ProductMetricsProcessor
        → product_metrics DB upsert  (R7)
        → Redis ZSET ZINCRBY         (R9)

[commerce-api]
  GET /api/v1/rankings?date=20260408&size=20&page=1
    → ZREVRANGE ranking:all:20260408
    → 상품 정보 aggregation
    → 응답

  GET /api/v1/products/{id}
    → 상품 정보 조회
    → ZREVRANK ranking:all:20260408 → 순위 포함 응답
```

---

## 7. 구현 체크리스트

### Phase 1. 이벤트 페이로드 변경

- [x] `PaymentFacade` — `productIds` → `products: [{productId, quantity}]`
- [x] `ProductMetricsConsumer` — 새 페이로드 파싱
- [x] `ProductMetricsProcessor` — quantity 파라미터 추가

#### 왜 바꿨나

랭킹 점수 공식에서 주문 이벤트의 score는 `0.7 * log1p(quantity)`다.
`log1p(quantity)`를 계산하려면 수량 정보가 필요한데, 기존 페이로드는 `productIds` 배열만 전달해 quantity가 없었다.
quantity 없이는 주문 1건(수량 1)과 주문 1건(수량 100)이 랭킹에 동일하게 반영되어 가중치 설계의 의미가 없어진다.

#### 무엇을 바꿨나

**`PaymentFacade`** — outbox 페이로드 구조 변경

```java
// Before
"productIds", order.getItems().stream().map(OrderItem::refProductId).toList()

// After
"products", order.getItems().stream()
    .map(item -> Map.of("productId", item.refProductId(), "quantity", item.quantity()))
    .toList()
```

**`ProductMetricsConsumer`** — `products` 배열에서 productId, quantity 추출

```java
// Before
List<Integer> productIds = (List<Integer>) payload.get("productIds");
processor.process(eventId, eventType, productId.longValue(), occurredAt);

// After
List<Map<String, Object>> products = (List<Map<String, Object>>) payload.get("products");
Long productId = ((Number) product.get("productId")).longValue();
Integer quantity = ((Number) product.get("quantity")).intValue();
processor.process(eventId, eventType, productId, quantity, occurredAt);
```

비order 이벤트(`PRODUCT_VIEWED`, `LIKED` 등)는 quantity 개념이 없으므로 `null` 전달.

**`ProductMetricsProcessor`** — 메서드 시그니처에 `Integer quantity` 추가

```java
// Before
public void process(String eventId, String eventType, Long productId, ZonedDateTime occurredAt)

// After
public void process(String eventId, String eventType, Long productId, Integer quantity, ZonedDateTime occurredAt)
```

quantity는 Phase 2에서 ZSET 점수 계산(`0.7 * log1p(quantity)`)에 사용된다.

### Phase 2. Redis ZSET 랭킹 적재

- [ ] `RankingRepository` 인터페이스 (domain)
- [ ] `RankingRepositoryImpl` 구현 (infrastructure, Redis ZSET)
- [ ] Key 전략: `ranking:all:{yyyyMMdd}`, TTL 2일
- [ ] `ProductMetricsProcessor`에 ZSET 점수 갱신 로직 추가

### Phase 3. 랭킹 API

- [ ] `GET /api/v1/rankings` — 날짜/페이지 기반 Top-N 조회 + 상품 정보 aggregation
- [ ] `GET /api/v1/products/{id}` — 상품 상세에 순위 필드 추가

### Phase 4. 콜드 스타트 완화 (Nice-to-Have)

- [ ] 23:50 스케줄러 — 전날 점수 10% carry-over (`ZUNIONSTORE`)
 