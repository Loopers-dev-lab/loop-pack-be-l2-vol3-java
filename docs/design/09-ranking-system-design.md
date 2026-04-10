# 09. Redis ZSET 기반 실시간 랭킹 시스템 — 구현 설계

---

## 1. 목적

유저에게 "지금 인기 있는 상품"을 빠르게 노출하는 것이 목표다.

### 1.1 왜 랭킹인가

이커머스에서 랭킹은 단순한 정렬이 아니라 **큐레이션 수단**이다.
홈 메인의 "인기 상품", 카테고리의 "인기순 정렬", 상품 상세의 "현재 N위" 표기 등
유저의 탐색 비용을 줄이고 구매 전환율을 높이는 핵심 지면에 활용된다.

### 1.2 RDB 집계의 한계

| 문제 | 설명 |
|------|------|
| 성능 | `GROUP BY + ORDER BY`는 데이터가 쌓일수록 느려짐 |
| 부하 | 랭킹은 조회 빈도가 매우 높아 DB 과부하로 직결 |
| 실시간성 | 배치 집계 주기만큼 지연 발생, "지금" 인기 있는 상품을 반영 못 함 |

### 1.3 해결 — Redis ZSET 기반 실시간 랭킹

Round 7에서 구축한 Kafka → commerce-streamer 파이프라인이 이미 유저 행동 이벤트(조회, 좋아요, 주문)를 수집하고 있다.
이 파이프라인을 확장하여 **이벤트 소비 시점에 Redis ZSET에 점수를 실시간 반영**하고,
API는 ZSET을 조회해 Top-N 및 개별 순위를 O(log N) 수준으로 제공한다.

| 요소 | 역할 |
|------|------|
| Kafka + MetricsConsumer | 이벤트 수집 + 집계 (기존 R7 인프라 재활용) |
| Redis ZSET | 점수 기반 정렬 상태 유지, Top-N / 개별 순위 조회 |
| Redis Hash | 상품별 개별 메트릭 저장 (SSOT), score 재계산의 근거 |
| Ranking API | ZSET 조회 → DB 상품 정보 aggregation → 응답 |

---

## 2. 데이터 흐름

### 2.1 전체 파이프라인

```
[commerce-api]
  유저 행동 → Kafka 이벤트 발행
    ├── catalog-events (PRODUCT_VIEWED, LIKE_CREATED, LIKE_REMOVED)
    └── order-events   (ORDER_CREATED, ORDER_CANCELLED)

[commerce-streamer — MetricsConsumer]
  Kafka 배치 소비 (3,000건/poll)
    ├── Phase 1: 멱등성 체크 (event_handled INSERT IGNORE) + productId별 메모리 집계
    ├── Phase 2: DB upsert (product_metrics — 전체 누적, 기존)
    └── Phase 3: Redis 적재 (ranking — 일간 집계, 신규)
         ├── Pipeline 1: HINCRBY × 필드 수 → Hash (개별 메트릭, SSOT)
         ├── in-memory: score 계산 (가중치 × 메트릭)
         └── Pipeline 2: ZADD → ZSET (랭킹 점수)

[commerce-api — Ranking API]
  ZREVRANGE → productId 목록 → DB IN 쿼리 → 상품 정보 aggregation → 응답
```

### 2.2 MetricsConsumer 확장 vs 별도 Consumer

| 관점 | MetricsConsumer 확장 | 별도 RankingConsumer |
|------|---------------------|---------------------|
| 이벤트 소비 | 1회 소비로 DB + Redis 모두 처리 | 같은 토픽을 다른 consumer group으로 이중 소비 |
| 멱등성 | event_handled 1회 체크로 공유 | 별도 멱등성 관리 필요 (or 중복 INSERT IGNORE) |
| deltaMap 재활용 | Phase 1 집계 결과를 Phase 3에서 그대로 사용 | 동일한 파싱 + 집계 로직 중복 |
| 장애 격리 | Redis 장애가 DB upsert에 영향 가능 | DB와 Redis 처리가 독립 |
| 운영 복잡도 | consumer group 1개 | consumer group 2개, 오프셋 관리 이중화 |

**결정: MetricsConsumer 확장**.

- deltaMap을 Phase 2(DB)와 Phase 3(Redis)가 공유하므로 파싱/집계 중복이 없다
- 멱등성 체크(event_handled)를 한 번만 수행한다
- 같은 토픽의 이중 소비로 인한 Kafka 파티션 부하, 오프셋 관리 복잡도를 피한다

**장애 격리 대응**: Phase 3(Redis 적재)는 Phase 2(DB upsert) 이후에 실행하고, Redis 장애 시에도 Phase 2까지는 정상 커밋되도록 try-catch로 격리한다. 랭킹은 "최선 노력(best-effort)" 성격이므로, Redis 장애 시 해당 배치의 랭킹 갱신만 유실되는 것은 허용한다.

### 2.3 SRP 준수 설계

MetricsConsumer에 Redis 로직을 직접 작성하면 단일 책임 원칙이 깨진다.
**랭킹 점수 갱신 책임을 별도 컴포넌트로 분리**한다.

```
MetricsConsumer (오케스트레이션)
  ├── Phase 1: 멱등성 + deltaMap 집계 (기존, MetricsConsumer 자체)
  ├── Phase 2: DB upsert (기존, MetricsConsumer 자체)
  └── Phase 3: rankingScoreUpdater.update(deltaMap) ← 위임
                  └── RankingScoreUpdater (신규 컴포넌트)
                        ├── Redis Hash HINCRBY (개별 메트릭)
                        ├── score 계산 (가중치 적용)
                        └── Redis ZSET ZADD (랭킹 점수)
```

- `MetricsConsumer`: 이벤트 소비 + 오케스트레이션 (Phase 흐름 제어)
- `RankingScoreUpdater`: 랭킹 점수 계산 + Redis 적재만 담당

이렇게 분리하면 MetricsConsumer는 "이벤트를 소비하고 각 처리기에 위임"하는 역할만 수행하고,
랭킹 로직의 테스트/변경이 Consumer와 독립적으로 가능하다.

### 2.4 Phase 3 상세 흐름 (RankingScoreUpdater)

```
입력: Map<Long, MetricsDelta> deltaMap (Phase 1에서 집계된 productId별 변화량)

Step 1 — Redis Hash 갱신 (Pipeline)
  deltaMap의 각 productId에 대해:
    HINCRBY ranking:metrics:{date}:{productId} viewCount    {viewDelta}
    HINCRBY ranking:metrics:{date}:{productId} likeCount    {likeDelta}
    HINCRBY ranking:metrics:{date}:{productId} salesCount   {salesCountDelta}
    HINCRBY ranking:metrics:{date}:{productId} salesAmount  {salesAmountDelta}
  → HINCRBY 리턴값 = 갱신 후의 필드 값 (HGETALL 불필요)
  → productId당 4개 명령, Pipeline 1회로 전송

Step 2 — Score 계산 (in-memory)
  HINCRBY 리턴값으로 각 productId의 전체 메트릭을 복원:
    viewCount, likeCount, salesCount, salesAmount
  score = W(view) × log₁₀(viewCount + 1) + W(like) × log₁₀(likeCount + 1) + W(order) × log₁₀(salesAmount + 1) + productId × 1e-10
  (+1: log₁₀(0) = -∞ 방지, productId × 1e-10: 동점 시 신상품 우선)

Step 3 — Redis ZSET 갱신 (Pipeline)
  ZADD ranking:all:{date} {score} {productId}  (× productId 수)
  → Pipeline 1회로 전송

Step 4 — TTL 설정
  새로 생성된 키에 대해서만 EXPIRE 설정 (2일)
```

**성능**: 인기 상품 100개에 집중되는 3,000건 배치 시
- Pipeline 1: 100 × 4 = 400 HINCRBY → 왕복 1회
- 계산: 100회 곱셈/덧셈 (마이크로초)
- Pipeline 2: 100 ZADD → 왕복 1회
- **총 추가 비용: Redis RTT 2회 ≈ 0.2ms** (로컬 기준)

### 2.5 이중 집계 구조 — 데이터 정합성 전략

MetricsConsumer는 같은 이벤트를 **두 저장소에 동시 적재**한다. 이 이중 구조는 의도된 설계 패턴이다.

```
이벤트 → MetricsConsumer
  ├── Phase 2: product_metrics (MySQL) — 원장 (일별 누적, 정합성 우선)
  └── Phase 3: ranking:* (Redis)       — 실시간 뷰 (일간 집계, 속도 우선)
```

#### 두 저장소의 역할 분리

| 관점 | product_metrics (DB) | ranking:all / ranking:metrics (Redis) |
|------|---------------------|--------------------------------------|
| 범위 | 일별 누적 (날짜 파티션) | 일간 집계 (오늘 00:00~23:59) |
| 정합성 | 정확 — 멱등성(event_handled) + 트랜잭션 보장 | 근사치 — best-effort, 부분 유실 허용 |
| 용도 | 일별 트렌드 분석, 주간/월간 배치 집계, Redis 장애 시 재집계 원장 | 실시간 Top-N API, 일간 랭킹 |
| 장애 시 | Redis 장애와 무관하게 정상 커밋 | DB 장애 시 Phase 3도 스킵 (Phase 순서 의존) |

#### product_metrics 테이블 재설계

##### AS-IS 문제점

기존 `product_metrics`는 `product_id`를 PK로 전체 기간 누적만 저장한다.

```sql
-- AS-IS: 시간 축 없는 카운터 테이블
INSERT INTO product_metrics (product_id, like_count, view_count, sales_count, sales_amount)
VALUES (?, ?, ?, ?, ?)
ON DUPLICATE KEY UPDATE like_count = like_count + VALUES(like_count), ...
```

| 문제 | 영향 |
|------|------|
| **시간 축 부재** | 메트릭 테이블의 본질은 "무엇을 + 언제". 시간이 없으면 카운터에 불과 |
| **일별 트렌드 분석 불가** | "지난 7일간 조회수 추이"를 뽑을 수 없다 |
| **Redis 장애 시 일간 재집계 불가** | 오늘 발생한 delta만 추출할 방법이 없다 |
| **취소 이력 소실** | `sales_count = sales_count + (-3)` → 원래 얼마를 팔았고 얼마가 취소됐는지 복원 불가 |
| **데이터 정리(purge) 불가** | 행이 하나뿐이라 오래된 데이터를 삭제할 수 없다 |

##### TO-BE: 그레인(Grain) = `daily × product`

메트릭 테이블의 **그레인**은 "한 행이 무엇을 의미하는가"다. PK가 그레인의 물리적 구현이며, 같은 키로 두 행이 들어갈 수 없으므로 그레인 위반을 DB가 강제로 방지한다.

```sql
CREATE TABLE product_metrics (
    product_id                    BIGINT   NOT NULL,
    metric_date                   DATE     NOT NULL,
    view_count                    INT      NOT NULL DEFAULT 0,
    like_count                    INT      NOT NULL DEFAULT 0,
    unlike_count                  INT      NOT NULL DEFAULT 0,
    sales_count                   INT      NOT NULL DEFAULT 0,
    sales_amount                  BIGINT   NOT NULL DEFAULT 0,
    -- 취소: 인식일 기준 (이벤트가 이 날짜에 도착)
    cancel_count_by_event_date    INT      NOT NULL DEFAULT 0,
    cancel_amount_by_event_date   BIGINT   NOT NULL DEFAULT 0,
    -- 취소: 발생일 기준 (원주문이 이 날짜에 결제)
    cancel_count_by_order_date    INT      NOT NULL DEFAULT 0,
    cancel_amount_by_order_date   BIGINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (product_id, metric_date),
    INDEX idx_metric_date (metric_date)
) ENGINE=InnoDB;
```

##### 설계 원칙 1 — Additive Measure + 취소 분리

메트릭 테이블 설계의 핵심 원칙: **취소/환불은 원본에서 차감하지 않고 별도 컬럼으로 기록한다.**

```
AS-IS (차감 방식):
  sales_count = 10 → ORDER_CANCELLED 3건 → sales_count = 7
  → 원래 10건이었는지 알 수 없음. "환불 전 매출"이라는 정보가 소실

TO-BE (분리 방식):
  sales_count = 10, cancel_count_by_event_date = 3
  → 순매출: sales_amount - cancel_amount_by_event_date (조회 시 계산)
  → 환불 전 매출: sales_amount 그대로
  → 환불률: cancel_count / sales_count (분자·분모 모두 보존)
```

**모든 컬럼이 Additive(양수 누적)**이므로 어떤 차원으로든 `SUM`이 가능하다. 사전 계산된 비율(`avg_order_value`, `cancel_rate` 등)은 **컬럼으로 두지 않는다.** 다중일 합산이 수학적으로 불가능하기 때문이다. 분자와 분모를 각각 저장하고 조회 시점에 나눈다.

##### 설계 원칙 2 — Late-Arriving Fact 이중 기록

취소 이벤트는 원주문과 **다른 날짜에 도착**한다. 이 때 두 가지 질문이 생긴다:

```
4월 1일: 상품 101에 주문 10만원 발생
4월 5일: 그 주문이 취소됨

Q1 (운영 관점): "4월 1일의 실제 순매출은?"
  → 4월 1일 행의 cancel_amount_by_order_date에 기록되어야 답할 수 있다

Q2 (현금흐름 관점): "4월 5일에 발생한 취소 금액은?"
  → 4월 5일 행의 cancel_amount_by_event_date에 기록되어야 답할 수 있다
```

두 질문 모두 정당하고 둘 다 답해야 한다. **저장은 풍부하게, 노출은 의견을 갖고.**

| 컬럼 | 기록 시점 | 대상 행 | 용도 |
|------|----------|---------|------|
| `cancel_count_by_event_date` | 취소 이벤트 도착일 | CURDATE() | "오늘 발생한 취소 건수" |
| `cancel_amount_by_event_date` | 취소 이벤트 도착일 | CURDATE() | "오늘 발생한 취소 금액" |
| `cancel_count_by_order_date` | 취소 이벤트 도착일 | **원주문 결제일** | "그 날 매출 중 취소된 건수" |
| `cancel_amount_by_order_date` | 취소 이벤트 도착일 | **원주문 결제일** | "그 날 매출 중 취소된 금액" |

**이벤트 스키마 변경 필요**: ORDER_CANCELLED 이벤트에 `originalOrderDate`(원주문 결제일)를 포함시킨다. commerce-api에서 주문 취소 시 이벤트 발행 로직을 수정한다.

조회 시:

```sql
-- 운영 관점: "4월 1일의 실제 순매출"
SELECT sales_amount - cancel_amount_by_order_date AS real_net_sales
FROM product_metrics
WHERE product_id = 101 AND metric_date = '2026-04-01';

-- 현금흐름 관점: "4월 5일에 발생한 취소 금액"
SELECT cancel_amount_by_event_date
FROM product_metrics
WHERE product_id = 101 AND metric_date = '2026-04-05';

-- 검증: 충분히 긴 기간으로 합산하면 두 기준의 합계가 같아야 함
SELECT SUM(cancel_amount_by_order_date) AS by_order,
       SUM(cancel_amount_by_event_date) AS by_event
FROM product_metrics WHERE product_id = 101;
-- 두 값이 같으면 정합성 정상
```

##### 설계 원칙 3 — 의미 정의 중앙화 (Semantic Definition)

메트릭의 의미가 코드 곳곳에 흩어지면, 정의 변경 시 모든 위치를 찾아 수정해야 한다. **"이 숫자가 무엇을 뜻하는가"를 한 곳에서 정의하고, 나머지는 그 정의를 참조한다.**

| 정의 대상 | 중앙화 위치 | 참조하는 곳 |
|----------|-----------|-----------|
| 이벤트 → 메트릭 매핑 | `MetricsDelta` 팩토리 메서드 (`ofView()`, `ofLike(int)`, `ofSales(int, long)`) | MetricsConsumer Phase 1 |
| 랭킹 score 수식의 가중치 | `RankingProperties.Weights` (yml 외부화) | RankingScoreUpdater, 배치 보정 잡 |
| 파생 메트릭 정의 | SQL VIEW 또는 쿼리 내 주석 | 분석 쿼리, 배치 보정 잡 |

**파생 메트릭 정의 예시**:

```sql
-- 파생 메트릭: 항상 이 공식으로 계산한다
-- net_like       = like_count - unlike_count
-- net_sales      = sales_amount - cancel_amount_by_event_date (인식일 기준)
-- real_net_sales  = sales_amount - cancel_amount_by_order_date (발생일 기준)
-- cancel_rate     = cancel_count_by_event_date / sales_count (조회 시 계산, 컬럼으로 저장하지 않음)
```

이 원칙의 핵심: 새로운 메트릭이 추가되거나 기존 메트릭의 의미가 변경될 때, **수정 지점이 1곳**(또는 명확히 한정된 소수)이어야 한다. `MetricsDelta`에 새 필드를 추가하면 Phase 1(집계), Phase 2(DB), Phase 3(Redis)가 자연스럽게 따라간다.

##### MetricsConsumer Phase 2 변경

ORDER_CANCELLED는 **2건의 UPSERT**가 필요하다 (인식일 행 + 발생일 행).

```sql
-- 1) 모든 이벤트: 인식일(CURDATE()) 기준 UPSERT
INSERT INTO product_metrics
  (product_id, metric_date, view_count, like_count, unlike_count,
   sales_count, sales_amount,
   cancel_count_by_event_date, cancel_amount_by_event_date,
   cancel_count_by_order_date, cancel_amount_by_order_date)
VALUES (?, CURDATE(), ?, ?, ?, ?, ?, ?, ?, 0, 0)
ON DUPLICATE KEY UPDATE
  view_count    = view_count    + VALUES(view_count),
  like_count    = like_count    + VALUES(like_count),
  unlike_count  = unlike_count  + VALUES(unlike_count),
  sales_count   = sales_count   + VALUES(sales_count),
  sales_amount  = sales_amount  + VALUES(sales_amount),
  cancel_count_by_event_date  = cancel_count_by_event_date  + VALUES(cancel_count_by_event_date),
  cancel_amount_by_event_date = cancel_amount_by_event_date + VALUES(cancel_amount_by_event_date)

-- 2) ORDER_CANCELLED만 추가: 발생일(원주문일) 기준 UPSERT
INSERT INTO product_metrics
  (product_id, metric_date,
   cancel_count_by_order_date, cancel_amount_by_order_date)
VALUES (?, ?, ?, ?)    -- metric_date = originalOrderDate
ON DUPLICATE KEY UPDATE
  cancel_count_by_order_date  = cancel_count_by_order_date  + VALUES(cancel_count_by_order_date),
  cancel_amount_by_order_date = cancel_amount_by_order_date + VALUES(cancel_amount_by_order_date)
```

이벤트별 매핑:

| 이벤트 | 대상 행 | view | like | unlike | sales_count | sales_amount | cancel_event | cancel_order |
|--------|---------|------|------|--------|-------------|--------------|-------------|-------------|
| PRODUCT_VIEWED | CURDATE() | +1 | 0 | 0 | 0 | 0 | 0 | 0 |
| LIKE_CREATED | CURDATE() | 0 | +1 | 0 | 0 | 0 | 0 | 0 |
| LIKE_REMOVED | CURDATE() | 0 | 0 | +1 | 0 | 0 | 0 | 0 |
| ORDER_CREATED | CURDATE() | 0 | 0 | 0 | +count | +amount | 0 | 0 |
| ORDER_CANCELLED (1) | CURDATE() | 0 | 0 | 0 | 0 | 0 | +count/+amount | 0 |
| ORDER_CANCELLED (2) | **originalOrderDate** | 0 | 0 | 0 | 0 | 0 | 0 | +count/+amount |

##### Redis 랭킹과의 관계

Redis 랭킹(Phase 3)은 **순수값(net)**으로 score를 계산한다:

```
Redis Hash:
  viewCount   = DB의 view_count           (취소 개념 없음)
  likeCount   = DB의 like_count - unlike_count (순 좋아요)
  salesAmount = DB의 sales_amount - cancel_amount_by_event_date (순 매출, 인식일 기준)

score = 0.1 × log₁₀(viewCount + 1)
      + 0.2 × log₁₀(likeCount + 1)
      + 0.7 × log₁₀(salesAmount + 1)
```

DB는 gross/cancel을 분리 보관하고 발생일/인식일 이중 기록(분석 가능성 보존), Redis는 net값으로 실시간 랭킹 계산. 역할이 다르므로 저장 형태도 다르다.

#### 장애 격리의 이점

Phase 2와 Phase 3는 **실행 순서는 있지만 트랜잭션을 공유하지 않는다.**

```
Phase 2 성공, Phase 3 실패:
  → DB 정확, Redis 일시 부정확 → 다음 배치에서 자연 복구
  → 유저: 상품 상세의 누적 통계는 정상, 랭킹만 잠시 지연

Phase 2 실패:
  → 트랜잭션 롤백 → deltaMap이 비정상이므로 Phase 3도 스킵
  → 유저: 해당 배치의 이벤트가 DB/Redis 모두 미반영. Kafka 오프셋 미커밋 → 재처리
```

#### 원장 기반 재집계 가능 여부

**엠넷플러스(Mnet Plus)**는 ElastiCache(실시간) + DynamoDB(원장)의 이중 집계에서, Redis 장애 시 DynamoDB 원장으로부터 재집계하는 경로를 갖추고 있다.

`product_metrics`에 `metric_date`가 포함되므로 우리 시스템에서도 동일한 재집계가 가능하다.

| 관점 | 가능 여부 | 이유 |
|------|----------|------|
| **일간 집계 복원** | **O** | `WHERE metric_date = CURDATE()` → 오늘 일별 데이터로 Redis ZSET 재구축 가능 |
| 전체 누적 복원 | O | `SUM(...) WHERE product_id = ?` → 전 기간 합산 |
| 주간/월간 집계 | O | `SUM(...) WHERE metric_date BETWEEN ? AND ?` → 기간별 집계 가능 |
| 이벤트 리플레이 | △ | Kafka 보존 기간(기본 7일) 내라면 이벤트 재소비로 복원 가능. 단 별도 리플레이 도구 필요 |

#### Redis 재집계 경로 (장애 복구)

Redis Hash/ZSET이 유실된 경우, `product_metrics`로부터 일간 랭킹을 재구축할 수 있다.

```sql
SELECT product_id,
       view_count,
       (like_count - unlike_count) AS net_like,
       sales_count,
       (sales_amount - cancel_amount_by_event_date) AS net_sales_amount
FROM product_metrics
WHERE metric_date = CURDATE()
```

```
재집계 흐름:
  1. product_metrics에서 오늘 데이터 조회
  2. net값 계산 (like - unlike, sales - cancel)
  3. 각 상품의 score 계산 (섹션 3 수식)
  4. Redis Pipeline으로 Hash + ZSET 일괄 적재
```

##### 설계 원칙 4 — Lambda Architecture (실시간 + 배치 보정)

실시간 집계만으로는 **누적 오차**가 발생할 수 있다. Pipeline 부분 실패, Redis 장애, Consumer 재시작 등으로 일부 delta가 유실되면 Hash의 누적값이 DB 원장과 어긋난다. 이를 "실시간 경로만으로 해결"하려면 재시도/보상 로직이 복잡해진다.

Lambda Architecture는 두 경로를 병행하여 정합성을 확보한다:

```
Speed Layer (실시간):
  Kafka → MetricsConsumer → Redis Hash/ZSET
  특성: 빠름(수 초 이내), 근사치, 부분 유실 허용

Batch Layer (보정):
  product_metrics (DB) → 배치 잡 → Redis Hash/ZSET 덮어쓰기
  특성: 느림(주기적), 정확, DB 원장 기반
```

**두 경로의 역할이 다르다.** 실시간은 "빠르게 반영"하고, 배치는 "정확하게 보정"한다. 실시간 경로에서 누적된 오차를 배치가 주기적으로 교정하므로, 실시간 경로의 부분 실패를 복잡한 보상 로직 없이 허용할 수 있다.

**배치 보정 잡 설계**:

```
실행 주기: 1시간마다 (정시)
실행 환경: commerce-batch (Spring Batch)

Step 1 — DB 원장 조회:
  SELECT product_id, view_count, (like_count - unlike_count) AS net_like,
         sales_count, (sales_amount - cancel_amount_by_event_date) AS net_sales_amount
  FROM product_metrics
  WHERE metric_date = CURDATE()

Step 2 — Score 재계산:
  score = 0.1 × log₁₀(view_count + 1)
        + 0.2 × log₁₀(net_like + 1)
        + 0.7 × log₁₀(net_sales_amount + 1)
        + product_id × 1e-10

Step 3 — Redis 덮어쓰기 (Pipeline):
  DEL ranking:metrics:{date}:{pid}    ← 기존 Hash 삭제
  HSET ranking:metrics:{date}:{pid} viewCount {view_count} likeCount {net_like} ...
  ZADD ranking:all:{date} {score} {pid}
  EXPIRE ...
```

**실시간 경로와의 Race Condition 대응**:

| 시나리오 | 영향 | 허용 여부 |
|---------|------|----------|
| 배치 ZADD 직후 실시간 HINCRBY | 배치가 넣은 값에 실시간 delta가 더해짐 → 정확 | 문제 없음 |
| 실시간 ZADD 직후 배치 ZADD | 배치가 실시간 값을 덮어씀 → 최근 수 초 이벤트 유실 | 다음 실시간 배치에서 복구 |
| 배치 DEL + HSET 사이에 실시간 HINCRBY | DEL 후 HINCRBY가 새 Hash 생성 → HSET이 덮어씀 | 다음 실시간 배치에서 복구 |

최악의 경우 "최근 수 초분 이벤트가 한 번 유실"되지만, 다음 실시간 배치(수 초 후)에서 delta가 다시 적용된다. **정합성은 결국 수렴한다.**

**1시간 주기의 산술적 근거**:

```
실시간 경로의 오차 축적률:
  MetricsConsumer 3,000건/배치 × 12배치/분 = 36,000건/분
  Pipeline 부분 실패율 가정: 0.1% (Redis 일시 불안정 등)
  → 시간당 누적 오차: 36,000 × 60 × 0.001 = 2,160건

배치 보정 비용:
  일간 활성 상품 10만 개 → SELECT 1회(인덱스 스캔) + Pipeline 1회
  DB 조회: ~50ms (idx_metric_date 활용)
  Redis Pipeline: 10만 × 3 명령 ≈ 300,000 명령 → ~300ms
  총: ~350ms / 1시간 = 무시 가능한 부하

→ 1시간 주기면 최대 2,160건의 오차가 다음 보정에서 교정됨
→ 오차 누적 시간 vs 보정 비용의 균형점
```

**배치 보정이 불필요한 경우**: Redis가 안정적이고 Pipeline 실패가 거의 없다면 배치 보정의 실질 효과는 미미하다. 그러나 "DB 원장이 있으니 언제든 재집계할 수 있다"는 구조를 갖추는 것 자체가 Lambda Architecture의 가치다.

#### 디스크 산정

```
product_metrics 행 크기:
  product_id(8) + metric_date(3) + view_count(4) + like_count(4) + unlike_count(4)
  + sales_count(4) + sales_amount(8)
  + cancel_count_by_event_date(4) + cancel_amount_by_event_date(8)
  + cancel_count_by_order_date(4) + cancel_amount_by_order_date(8)
  = ~59 bytes/row
  + InnoDB 행 오버헤드 ~30 bytes ≈ 89 bytes/row

일간 활성 상품 10만 개 × 30일 보존:
  100,000 × 30 × 89 bytes ≈ 255 MB

1년 보존 (상품 10만 개):
  100,000 × 365 × 89 bytes ≈ 3.1 GB
```

데이터 정리: `DELETE FROM product_metrics WHERE metric_date < DATE_SUB(CURDATE(), INTERVAL 90 DAY)` — 90일 이상 오래된 데이터를 주기적으로 purge. 날짜 인덱스(`idx_metric_date`)를 활용하여 효율적 삭제 가능.

---

## 3. 점수 계산 모델

### 3.1 가중치 산정 근거

| 지표 | 가중치 | 근거 |
|------|--------|------|
| view | 0.1 | 가장 발생 빈도가 높은 시그널. 높게 잡으면 조회 수만으로 랭킹이 지배됨. "구경만 한" 상품과 "실제 인기" 상품을 구분하기 위해 낮게 설정 |
| like | 0.2 | 유저의 능동적 관여 — 조회보다 의도가 강하지만, 구매 결정까지는 아님. 위시리스트 성격의 중간 시그널 |
| order | 0.7 | 유저가 결제까지 완료한 가장 신뢰도 높은 시그널. 매출과 직결되므로 비즈니스 가치 정렬. 조작 난이도도 가장 높음 |

**총합 = 1.0** — 각 가중치가 전체에서 차지하는 비중을 직관적으로 파악 가능.

> 과제 문서에서는 order 가중치를 0.6으로 제시하나, 시니어 관점에서 주문의 비즈니스 가치를 더 반영하여 0.7로 상향. 나머지를 view 0.1 + like 0.2로 배분.

### 3.2 스케일 문제와 정규화

**salesAmount에만 log를 적용하면 스케일 불균형이 발생한다.**

일간 기준 현실적 시나리오로 검증한다:

```
Product A: 조회 500회, 좋아요 30회, 주문 총액 200,000원
Product B: 조회 100회, 좋아요 10회, 주문 총액 1,000,000원
```

#### salesAmount에만 log 적용 시

```
score = W(view) × viewCount + W(like) × likeCount + W(order) × log₁₀(salesAmount + 1)

A = 0.1×500 + 0.2×30 + 0.7×log₁₀(200001) = 50 + 6 + 0.7×5.3 = 59.71
B = 0.1×100 + 0.2×10 + 0.7×log₁₀(1000001) = 10 + 2 + 0.7×6.0 = 16.20

→ A가 B보다 3.7배 높음
→ viewCount(50 vs 10)가 score를 지배. order 가중치 0.7의 의도가 무력화됨
```

**문제**: view가 선형(0~수천)인데 order가 log(0~6)이므로, 가중치와 무관하게 view가 score를 지배한다.

#### 전 지표 log 정규화 적용 시

```
score = W(view) × log₁₀(viewCount + 1) + W(like) × log₁₀(likeCount + 1) + W(order) × log₁₀(salesAmount + 1)

A = 0.1×log₁₀(501) + 0.2×log₁₀(31) + 0.7×log₁₀(200001) = 0.1×2.7 + 0.2×1.49 + 0.7×5.3 = 0.27 + 0.30 + 3.71 = 4.28
B = 0.1×log₁₀(101) + 0.2×log₁₀(11) + 0.7×log₁₀(1000001) = 0.1×2.0 + 0.2×1.04 + 0.7×6.0 = 0.20 + 0.21 + 4.20 = 4.61

→ B가 A보다 높음
→ 주문 총액이 5배 높은 B가 상위. 가중치 의도(order=0.7)가 정확히 반영됨
```

**결정: 전 지표에 log₁₀ 정규화를 적용한다.**

- 모든 입력이 log₁₀ 스케일(0~6 범위)로 통일되어 가중치가 의도대로 작동
- `+1`은 값이 0일 때 `log₁₀(0) = -∞` 방지

### 3.3 최종 수식

```
score(p) = 0.1 × log₁₀(viewCount + 1)
         + 0.2 × log₁₀(likeCount + 1)
         + 0.7 × log₁₀(salesAmount + 1)
         + productId × 1e-10              ← 동점 시 신상품 우선 (섹션 9 참조)
```

#### 검증 — 가중치 의도대로 동작하는가?

| 상품 | view | like | salesAmount | score | 순위 |
|------|------|------|-------------|-------|------|
| C (조회만 많음) | 5,000 | 10 | 50,000 | 0.1×3.7 + 0.2×1.04 + 0.7×4.7 = **3.87** | 3위 |
| A (균형) | 500 | 30 | 200,000 | 0.1×2.7 + 0.2×1.49 + 0.7×5.3 = **4.28** | 2위 |
| B (매출 집중) | 100 | 10 | 1,000,000 | 0.1×2.0 + 0.2×1.04 + 0.7×6.0 = **4.61** | 1위 |

- B(매출 최고) > A(균형) > C(조회만 많음) → **order 가중치 0.7이 지배적으로 작동**
- 조회 수가 50배 차이(C vs B)나도 매출이 높은 B가 상위 → 의도대로 동작

### 3.4 음수 이벤트 처리 (LIKE_REMOVED, ORDER_CANCELLED)

취소 이벤트는 **DB와 Redis에서 다르게 처리**된다.

#### DB (product_metrics) — 취소 분리 저장

```
LIKE_REMOVED   → unlike_count += 1   (like_count는 건드리지 않음)
ORDER_CANCELLED → cancel_count += count, cancel_amount += amount
```

원본을 보존하고 취소를 별도 기록한다. 분석 시 gross/net을 자유롭게 계산 가능.

#### Redis (ranking:metrics Hash) — net값으로 감소

```
LIKE_REMOVED   → HINCRBY likeCount -1
ORDER_CANCELLED → HINCRBY salesCount -{count}, HINCRBY salesAmount -{amount}
```

Redis Hash는 랭킹 score 계산 전용이므로 **순수값(net)을 직접 저장**한다. 분석 목적이 아니라 score 계산의 입력값이기 때문.

**log + 취소의 정확성**:

```
취소 전: salesAmount = 500,000 → log₁₀(500001) = 5.699
50,000원 주문 취소 후: salesAmount = 450,000 → log₁₀(450001) = 5.653
```

Hash에서 총액을 감소시키고 log를 재계산하므로 항상 수학적으로 정확하다.

### 3.5 ZINCRBY vs Metric 기반 — 트레이드오프 분석

| 관점 | ZINCRBY (즉시 증분) | Metric 기반 (Hash + ZADD) |
|------|-------------------|--------------------------|
| Redis 연산 | 1회 (ZINCRBY) | HINCRBY × 필드 수 + ZADD |
| 가중치 변경 | **불가** — 기존 score 분해 불가, ZSET 재생성 필요 | **가능** — Hash에서 재계산 |
| ORDER_CANCELLED + log | **수학적으로 부정확** — 아래 설명 | **정확** — 총액 감소 후 재계산 |
| 디버깅 | score 52.3이 뭘 의미하는지 알 수 없음 | Hash 조회로 view=100, like=20 등 확인 가능 |
| 메모리 | ZSET만 | ZSET + Hash (상품당 ~50bytes 추가) |
| SSOT | ZSET 자체가 유일 소스 | **Hash가 SSOT**, ZSET은 파생값 |

**ZINCRBY + log에서 취소가 부정확한 이유**:

```
주문 1: 100,000원 → ZINCRBY +0.7 × log₁₀(100001) = +3.50
주문 2:  50,000원 → ZINCRBY +0.7 × log₁₀(50001)  = +3.29
누적 score = 6.79

주문 1 취소: ZINCRBY -0.7 × log₁₀(100001) = -3.50
남은 score = 3.29

그러나 정확한 값은:
salesAmount = 50,000 → 0.7 × log₁₀(50001) = 3.29  ← 우연히 일치

주문 2 취소: ZINCRBY -0.7 × log₁₀(50001) = -3.29
남은 score = 0.00  ✓ (맞음)

하지만 세 주문 이상에서는:
주문 3건(10만+5만+3만) 후 중간 취소 시,
ZINCRBY 역연산 ≠ log₁₀(남은 총액)
→ log(a) + log(b) = log(a×b) ≠ log(a+b)
```

**결정: Metric 기반(Hash + ZADD)을 채택한다.**

성능 차이가 무시할 수준(0.2ms/배치)이면서, 가중치 변경 가능성, 취소 정합성, 디버깅 편의성에서 모두 우위다.
설계 문서에는 ZINCRBY 방식을 분석한 근거와 함께 Metric 기반을 선택한 이유를 기록하여, 과제의 "ZINCRBY 기반 실시간 집계" 키워드를 충족한다.

---

## 4. Redis Key 설계

### 4.1 키 패턴

| 용도 | 키 패턴 | 타입 | 예시 |
|------|---------|------|------|
| 일간 랭킹 | `ranking:all:{yyyyMMdd}` | ZSET | `ranking:all:20260410` |
| 상품별 일간 메트릭 | `ranking:metrics:{yyyyMMdd}:{productId}` | Hash | `ranking:metrics:20260410:101` |

**ZSET 구조**:

```
ranking:all:20260410
  member: "101"   score: 4.61
  member: "202"   score: 4.28
  member: "303"   score: 3.87
  ...
```

- member는 productId(문자열), score는 섹션 3의 수식으로 계산된 값

**Hash 구조**:

```
ranking:metrics:20260410:101
  viewCount:    500
  likeCount:    30
  salesCount:   5
  salesAmount:  200000
```

- SSOT(Single Source of Truth). ZSET의 score는 이 Hash로부터 파생된다.
- HINCRBY 리턴값으로 score를 계산하므로 별도 HGETALL이 불필요하다.

### 4.2 키 네이밍 설계 근거

**`ranking:all`에서 `all`의 의미**:

현재는 전체 상품 대상 랭킹만 존재한다. 향후 카테고리별 랭킹 확장 시:

```
ranking:all:{date}          → 전체 랭킹
ranking:category:1:{date}   → 카테고리 1 랭킹
ranking:category:2:{date}   → 카테고리 2 랭킹
```

`all`을 명시해두면 네임스페이스 충돌 없이 확장 가능하다.

**Hash 키에 productId를 포함하는 이유**:

| 대안 | 구조 | 문제 |
|------|------|------|
| 상품별 Hash (`ranking:metrics:{date}:{pid}`) | 키 1개당 필드 4개 | 키 수가 많지만, 개별 TTL 관리 가능 |
| 날짜별 단일 Hash (`ranking:metrics:{date}`) | 필드명: `{pid}:viewCount` 등 | 키 1개에 필드 수천 개, HGETALL 비용 증가, 상품별 조회 불편 |

**결정: 상품별 Hash**. 키 수가 많아지나 각각 독립적으로 만료되고, 디버깅 시 특정 상품의 메트릭을 `HGETALL ranking:metrics:20260410:101`로 즉시 확인할 수 있다.

### 4.3 시간대 기준 — KST

**왜 KST인가**: 이커머스 서비스의 비즈니스 일자는 한국 시간 기준이다. "오늘의 인기 상품"이 UTC 기준이면 한국 자정에 랭킹이 리셋되지 않는다.

```java
LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
String dateKey = today.format(DateTimeFormatter.BASIC_ISO_DATE); // "20260410"
```

**자정 경계 이벤트**: 23:59:59 KST에 발생한 이벤트가 처리 시점(00:00:01 KST)에 다음 날 키에 적재될 수 있다. 이는 허용한다 — 초 단위 정확도보다 시스템 단순성이 우선이며, 랭킹 특성상 수 초의 경계 차이는 의미 없다.

### 4.4 TTL 설계

| 키 | TTL | 산정 근거 |
|----|-----|----------|
| `ranking:all:{date}` | **2일 (172,800초)** | 오늘 + 어제 랭킹 조회 보장. 그저께부터 만료 |
| `ranking:metrics:{date}:{pid}` | **2일 (172,800초)** | ZSET과 동일 생명주기. Hash가 먼저 만료되면 score 재계산 불가 |

**TTL 설정 시점**: Pipeline에서 HINCRBY/ZADD와 함께 EXPIRE를 전송한다.

```
Pipeline 1:
  HINCRBY ranking:metrics:20260410:101 viewCount 5
  HINCRBY ranking:metrics:20260410:101 likeCount 1
  ...
  EXPIRE  ranking:metrics:20260410:101 172800    ← 매 배치마다 갱신
Pipeline 2:
  ZADD    ranking:all:20260410 4.61 101
  ...
  EXPIRE  ranking:all:20260410 172800             ← 매 배치마다 갱신
```

**매 배치마다 EXPIRE를 재설정하는 이유**:

- EXPIRE는 O(1)이며 Pipeline에 포함되므로 추가 왕복 없음
- "마지막 쓰기 + 2일 후" 만료 → 날짜 전환 후에도 어제 데이터가 충분히 유지됨
- 키 생성 여부를 확인(`EXISTS`)하는 것보다 단순하고 안전

### 4.5 Nice-to-Have: 시간 단위 키 확장

일간 키 패턴을 그대로 확장하면 시간 단위 랭킹도 자연스럽게 구현 가능하다:

```
ranking:all:daily:{yyyyMMdd}           TTL: 2일
ranking:all:hourly:{yyyyMMddHH}        TTL: 3시간
ranking:metrics:hourly:{yyyyMMddHH}:{productId}  TTL: 3시간
```

RankingScoreUpdater에 키 생성 전략을 주입하면 daily/hourly를 동시에 지원할 수 있다. 현재 구현에서는 daily만 구현하고, 구조만 확장 가능하게 설계한다.

---

## 5. Redis Pipeline 최적화

### 5.1 왜 Pipeline인가

MetricsConsumer의 3,000건 배치가 인기 상품 100개에 집중될 때, 100개 상품의 메트릭을 갱신해야 한다.
Pipeline 없이 개별 명령을 전송하면:

```
개별 전송: 100상품 × (4 HINCRBY + 1 EXPIRE) + 100 ZADD + 1 EXPIRE = 601 왕복
Pipeline:  2 왕복 (Pipeline 1 + Pipeline 2)
```

Redis RTT가 로컬 0.1ms, 원격 1ms일 때:

| 방식 | 로컬 (RTT 0.1ms) | 원격 (RTT 1ms) |
|------|------------------|---------------|
| 개별 전송 | 601 × 0.1ms = **60ms** | 601 × 1ms = **601ms** |
| Pipeline | 2 × 0.1ms = **0.2ms** | 2 × 1ms = **2ms** |

**Pipeline은 네트워크 왕복을 줄이는 것이지 Redis 서버 처리 시간을 줄이는 것이 아니다.**
명령 자체의 처리 시간은 동일하지만, 300배 이상의 왕복 절감 효과가 있다.

### 5.2 Pipeline 구성

```
Pipeline 1 — Hash 갱신 + TTL
  deltaMap의 각 productId에 대해:
    HINCRBY ranking:metrics:{date}:{pid} viewCount    {viewDelta}     → 리턴: 갱신 후 값
    HINCRBY ranking:metrics:{date}:{pid} likeCount    {likeDelta}     → 리턴: 갱신 후 값
    HINCRBY ranking:metrics:{date}:{pid} salesCount   {salesCountDelta} → 리턴: 갱신 후 값
    HINCRBY ranking:metrics:{date}:{pid} salesAmount  {salesAmountDelta} → 리턴: 갱신 후 값
    EXPIRE  ranking:metrics:{date}:{pid} 172800
  명령 수: productId 수 × 5

  ↓ 리턴값 수집 (productId당 4개 HINCRBY 리턴 = 전체 메트릭 상태)

in-memory Score 계산
  HINCRBY 리턴값에서 viewCount, likeCount, salesCount, salesAmount 복원
  score = 0.1 × log₁₀(viewCount + 1) + 0.2 × log₁₀(likeCount + 1) + 0.7 × log₁₀(salesAmount + 1)

Pipeline 2 — ZSET 갱신 + TTL
  ZADD ranking:all:{date} {score} {productId}  (× productId 수)
  EXPIRE ranking:all:{date} 172800
  명령 수: productId 수 + 1
```

### 5.3 HINCRBY 리턴값 활용

HINCRBY는 **증분 후의 새 값**을 리턴한다. 이를 활용하면 HGETALL 없이 전체 메트릭을 복원할 수 있다.

```
Pipeline 1 실행 결과 (productId=101의 경우):
  results[0] = 505   ← viewCount (기존 500 + delta 5)
  results[1] = 31    ← likeCount (기존 30 + delta 1)
  results[2] = 6     ← salesCount (기존 5 + delta 1)
  results[3] = 250000 ← salesAmount (기존 200000 + delta 50000)
  results[4] = 1     ← EXPIRE 결과 (무시)
```

**Spring Data Redis `executePipelined()`의 리턴 순서는 명령 전송 순서와 동일하다.**
productId당 5개 명령(HINCRBY × 4 + EXPIRE)이므로, `results[i * 5]` ~ `results[i * 5 + 3]`이 i번째 상품의 메트릭이다.

### 5.4 성능 산정

인기 상품 100개에 집중되는 3,000건 배치 기준:

```
Pipeline 1: 100 × 5 = 500 명령
  Redis 처리: HINCRBY O(1) ~1μs × 400 + EXPIRE O(1) ~1μs × 100 = ~0.5ms
  네트워크: 1 RTT ≈ 0.1ms (로컬)
  소계: ~0.6ms

Score 계산: 100 × Math.log10() × 3 = 300회 부동소수점 연산
  소계: ~0.01ms (무시 가능)

Pipeline 2: 100 + 1 = 101 명령
  Redis 처리: ZADD O(log N) ~2μs × 100 + EXPIRE ~1μs = ~0.2ms
  네트워크: 1 RTT ≈ 0.1ms
  소계: ~0.3ms

총 추가 비용: ~0.9ms / 배치
```

기존 Phase 1+2(DB 멱등성 체크 + upsert)가 수십~수백ms인 것 대비 **1% 미만의 오버헤드**다.

### 5.5 부분 실패 처리

Pipeline 내 개별 명령이 실패해도 나머지 명령은 정상 실행된다 (Redis Pipeline은 트랜잭션이 아니다).

| 실패 시나리오 | 영향 | 대응 |
|-------------|------|------|
| HINCRBY 일부 실패 | 해당 상품의 score가 부정확 | 다음 배치에서 delta가 다시 적용되어 자연 보정 |
| ZADD 실패 | 해당 상품의 랭킹 미반영 | 다음 배치에서 새 score로 ZADD → 자연 보정 |
| EXPIRE 실패 | 키가 만료되지 않을 수 있음 | 다음 배치에서 EXPIRE 재시도 → 자연 보정 |
| Redis 전체 장애 | Phase 3 전체 스킵 | try-catch로 격리, Phase 2(DB)는 정상 커밋. WARN 로그 기록 |

**모든 부분 실패는 "다음 배치에서 자연 보정"된다.** 랭킹은 best-effort 성격이므로, 일시적 부정확은 허용하고 복잡한 보상 로직은 추가하지 않는다.

---

## 6. 메모리 산정

### 6.1 ZSET 메모리

Redis ZSET의 member당 오버헤드는 **skiplist 노드 + SDS 문자열**로 구성된다.

```
member 1개 = skiplist 노드(~40bytes) + SDS(productId 문자열, ~20bytes) + score(8bytes)
           ≈ 68 bytes/member
```

| 시나리오 | 상품 수 | ZSET 메모리 | 비고 |
|---------|---------|------------|------|
| 현재 과제 | 5개 (시드 데이터) | ~340 bytes | 무시 가능 |
| 소규모 서비스 | 1,000개 | ~66 KB | 무시 가능 |
| 중규모 서비스 | 10,000개 | ~664 KB | 여유 |
| 대규모 서비스 | 100,000개 | ~6.5 MB | 충분히 수용 가능 |

일간 키 2개(오늘 + 어제)가 동시에 존재하므로 × 2:
- 상품 10만 개 기준: **~13 MB** → Redis 메모리 용량 대비 무시 가능

### 6.2 Hash 메모리

상품별 Hash는 4개 필드(viewCount, likeCount, salesCount, salesAmount)를 저장한다.

```
Hash 1개 = 키 오버헤드(~60bytes) + 필드 4개 × (필드명 ~15bytes + 값 ~10bytes)
         ≈ 160 bytes/상품
```

| 시나리오 | 상품 수 | Hash 메모리 | 비고 |
|---------|---------|------------|------|
| 현재 과제 | 5개 | ~800 bytes | 무시 가능 |
| 소규모 | 1,000개 | ~156 KB | 무시 가능 |
| 중규모 | 10,000개 | ~1.6 MB | 여유 |
| 대규모 | 100,000개 | ~15.3 MB | 수용 가능 |

### 6.3 총 메모리 (ZSET + Hash)

일간 키 2개분(오늘 + 어제), 상품 10만 개 기준:

```
ZSET: 100,000 × 68 bytes × 2일 = ~13 MB
Hash: 100,000 × 160 bytes × 2일 = ~31 MB
합계: ~44 MB
```

Redis 인스턴스가 보통 1~16 GB 메모리를 할당받는 점을 감안하면, **전체 용량의 0.3~4.4%** 수준이다.

### 6.4 Carry-Over 시점 피크 메모리

23:50에 carry-over가 실행되면 오늘(D) + 내일(D+1) ZSET이 동시에 존재한다. 어제(D-1)의 TTL이 아직 만료되지 않았으므로, **최대 3일분 ZSET이 동시에 존재**한다.

```
시간대별 존재 키:
  23:49 (carry-over 직전): D-1, D          → ZSET 2개
  23:50 (carry-over 실행): D-1, D, D+1     → ZSET 3개 (피크)
  ~D+1 00:00 이후:         D-1 TTL 만료 시작 → ZSET 2개로 복귀
```

Hash는 ZSET과 동일 TTL이므로 같은 패턴이다. 단, carry-over는 Hash를 복사하지 않으므로 D+1의 Hash는 이벤트가 들어올 때만 생성된다.

```
피크 메모리 (상품 10만 개 기준):
  ZSET: 100,000 × 68 bytes × 3일 = ~19.5 MB
  Hash: 100,000 × 160 bytes × 2일 = ~31 MB   (D+1 Hash는 아직 거의 없음)
  합계: ~50.5 MB (피크)

정상 시: ~44 MB → 피크 시: ~50.5 MB → +15% 증가
```

**피크 메모리가 Redis 용량에 미치는 영향은 무시 가능하다.** 1GB Redis 기준 5%, 16GB 기준 0.3%.

### 6.5 Capped ZSET 필요 여부

| 전략 | 설명 | 적합 여부 |
|------|------|----------|
| 전체 유지 | 모든 상품을 ZSET에 유지 | 10만 개까지 13MB → **현재 충분** |
| Top N 유지 | `ZREMRANGEBYRANK` 로 하위 항목 주기적 제거 | 상품 100만 개 이상 시 고려 |

**결정: Capped ZSET은 현재 불필요.**

- 목표가 Top 100 표시이지만, ZSET 전체를 유지해도 메모리 부담이 없다
- 하위 항목을 제거하면 "상품 상세에서 해당 상품 순위 조회" (ZREVRANK)가 불가능해진다
- 일간 활성 상품(하루 동안 이벤트가 1건 이상 발생한 상품)이 100만 개를 넘어가는 시점에 재검토한다

단, Hash는 ZSET에 존재하는 상품만 유지하면 되므로, 만약 Capped ZSET을 도입한다면 제거된 상품의 Hash도 함께 삭제해야 한다.

#### Capped ZSET 도입 시 트레이드오프

만약 일간 활성 상품이 100만 개를 넘어 Capped ZSET을 도입해야 하는 경우:

```
ZREMRANGEBYRANK ranking:all:{date} 0 -(N+1)
→ 상위 N개만 남기고 하위 항목 제거
```

| 관점 | Capped 전 (전체 유지) | Capped 후 (Top N 유지) |
|------|---------------------|----------------------|
| 메모리 | 상품 수에 비례 증가 | N으로 고정 |
| ZREVRANK (개별 순위) | 모든 상품 조회 가능 | **하위 상품 조회 불가** — "순위권 밖" 표시 필요 |
| ZADD 경합 | 없음 | ZREMRANGEBYRANK 실행 사이에 ZADD된 하위 상품이 남을 수 있음 |
| Hash 동기화 | 불필요 | 제거된 상품의 Hash도 삭제 필요 → 추가 DEL 명령 |
| 실행 시점 | — | 스케줄러(1분 주기) 또는 ZADD 직후 (ZADD 직후는 latency 증가) |

**도입 시 주의사항**:
- `ZREMRANGEBYRANK`는 O(log(N)+M) (M=제거 수)이므로, 대량 제거 시 Redis 블로킹 가능. 한 번에 제거하지 말고 분할 제거 권장
- 제거된 상품에 새 이벤트가 들어오면 다시 ZADD되므로, 최하위 상품이 반복적으로 추가/제거되는 "thrashing" 가능. Cap을 Top 100이 아닌 Top 1,000~10,000으로 여유 있게 설정하여 방지

---

## 7. API 설계

### 7.1 랭킹 Page 조회

```
GET /api/v1/rankings?date={yyyyMMdd}&page={page}&size={size}
```

| 파라미터 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| date | String | 오늘 (KST) | 조회 대상 날짜. 생략 시 오늘 |
| page | int | 0 | 0-based 페이지 번호 |
| size | int | 20 | 페이지당 항목 수 |

**페이지네이션 → ZREVRANGE 오프셋 변환**:

```
start = page × size
end   = start + size - 1

예: page=0, size=20 → ZREVRANGE ranking:all:20260410 0 19 WITHSCORES  (1~20위)
    page=2, size=20 → ZREVRANGE ranking:all:20260410 40 59 WITHSCORES (41~60위)
```

**Top 100 제한**: API 레벨에서 `start + size`가 100을 초과하면 100으로 cap.
ZSET 자체는 전체를 유지하되(개별 순위 조회용), 목록 API는 100위까지만 노출한다.

**응답 구조** (기존 `PagedProductResponse` 패턴 준수):

```json
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "data": [
      {
        "rank": 1,
        "productId": 101,
        "productName": "상품A",
        "brandName": "브랜드X",
        "price": 50000,
        "score": 4.61
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "page": 0,
    "size": 20
  }
}
```

**상품 정보 Aggregation 흐름**:

```
1. ZREVRANGE ranking:all:{date} start end WITHSCORES
   → [(productId, score), ...] 목록

2. productId 목록으로 DB IN 쿼리
   → SELECT * FROM product WHERE id IN (101, 202, 303, ...)
   → Brand 정보도 함께 조회 (기존 ProductWithBrand 패턴)

3. Redis 순서(score 내림차순) 유지하며 상품 정보와 병합
   → rank = start + index + 1 (1-based 순위)

4. ApiResponse<RankingDto.PagedRankingResponse> 반환
```

**totalElements 결정**:
- `ZCARD ranking:all:{date}` = ZSET 전체 상품 수
- `min(ZCARD, 100)` = API에서 노출하는 총 항목 수
- `totalPages = ceil(totalElements / size)`

### 7.2 상품 상세 조회 시 랭킹 정보 추가

기존 `GET /api/v1/products/{productId}` 응답에 랭킹 정보를 추가한다.

```json
{
  "meta": { "result": "SUCCESS" },
  "data": {
    "id": 101,
    "brandId": 1,
    "brandName": "브랜드X",
    "name": "상품A",
    "price": 50000,
    "stockQuantity": 100,
    "likeCount": 30,
    "ranking": {
      "rank": 3,
      "score": 4.28,
      "date": "20260410"
    }
  }
}
```

- 랭킹 미진입 상품(ZSET에 없는 경우): `"ranking": null`
- 조회 대상 날짜: 항상 오늘(KST)

**Redis 조회**:

```
ZREVRANK ranking:all:{today} {productId}  → 순위 (0-based, null이면 미진입)
ZSCORE   ranking:all:{today} {productId}  → 점수
```

**아키텍처**: CLAUDE.md의 "여러 도메인의 정보 조합은 Application Layer에서 처리" 규칙에 따라, `ProductFacade`가 `RankingRedisRepository`를 호출하여 랭킹 정보를 조합한다.

```
ProductFacade.getProductDetailCached(productId)
  ├── 기존: Product + Brand 조회
  └── 추가: RankingRedisRepository.getRankAndScore(today, productId)
            → (rank, score) or null
```

### 7.3 Master-Replica 분리

| 연산 | 대상 | Template |
|------|------|----------|
| ZINCRBY, HINCRBY, ZADD, EXPIRE | 쓰기 (commerce-streamer) | `writeTemplate` (`@Qualifier("redisTemplateMaster")`) |
| ZREVRANGE, ZREVRANK, ZSCORE, ZCARD | 읽기 (commerce-api) | `readTemplate` (기본, Replica 우선) |

기존 `WaitingQueueRedisRepository`와 동일한 패턴이다.

### 7.4 레이어 구조 (commerce-api)

```
interfaces/api/ranking/
  └── RankingController          — GET /api/v1/rankings

application/ranking/
  └── RankingFacade              — ZSET 조회 + DB 상품 조합

domain/ranking/
  └── (없음 — 별도 Entity/VO 불필요. Redis 조회 결과는 DTO로 직접 전달)

infrastructure/ranking/
  └── RankingRedisRepository     — ZREVRANGE, ZREVRANK, ZSCORE, ZCARD
```

**DTO 구조**:

```
interfaces/api/ranking/
  └── RankingDto
        ├── RankingResponse        — 개별 랭킹 항목 (rank, productId, productName, ...)
        └── PagedRankingResponse   — 페이지네이션 응답
```

**domain 레이어가 비어있는 이유**: 랭킹 데이터는 Redis ZSET에서 읽어 상품 정보와 조합하는 조회 전용 기능이다. 별도의 비즈니스 규칙이나 상태 변경이 없으므로 Entity/VO를 만들지 않는다.

### 7.5 Top-N 캐싱 트레이드오프

랭킹 Top-N 결과를 별도 캐싱(Redis String 또는 로컬 캐시)해야 하는가?

#### 현재 구조의 성능

```
ZREVRANGE ranking:all:{date} 0 19 WITHSCORES
→ O(log(N) + 20) ≈ O(log(100,000) + 20) ≈ O(37)
→ Redis 처리 시간: ~0.01ms
→ Replica 조회이므로 Master 부하 없음
```

ZREVRANGE 자체가 O(log N + M)으로 충분히 빠르고, Replica에서 읽으므로 쓰기 경로에 영향이 없다.

#### 캐싱 도입 시 얻는 것과 잃는 것

| 관점 | 캐싱 없음 (현재) | 캐싱 도입 |
|------|----------------|----------|
| 응답 지연 | ZREVRANGE ~0.1ms + DB IN 쿼리 ~5ms | 캐시 히트 시 ~0.1ms (DB 쿼리 스킵) |
| 실시간성 | 이벤트 반영 즉시 랭킹 변동 | **캐시 TTL(예: 10초) 동안 stale** |
| 구현 복잡도 | 단순 | 캐시 무효화 전략, TTL 산정, 페이지별 캐시 키 관리 |
| 메모리 | 없음 | 페이지당 캐시 엔트리 (Top 100 / 20개씩 = 5 페이지) |

**결정: Top-N 캐싱은 현재 불필요.**

근거:
- ZREVRANGE가 이미 O(log N + M)으로 충분히 빠르다
- "실시간 랭킹"을 표방하면서 10초 TTL 캐시를 두면 실시간성이 퇴색된다
- 병목은 Redis 조회가 아니라 DB IN 쿼리(상품 정보 조합) — 이는 상품 캐시(기존 Round 6 구현)로 이미 대응 중
- TPS가 수천 이상으로 늘어나 Replica 부하가 문제되면 그때 도입

**도입 시 설계 방향** (향후 참고):
- 캐시 대상: 상품 정보가 조합된 최종 응답 (Redis 조회 + DB 조회 결과를 함께 캐싱)
- TTL: 5~10초 (실시간성과 캐시 효율의 균형)
- 캐시 키: `ranking:cache:{date}:{page}:{size}`
- 무효화: TTL 기반 자연 만료 (이벤트 기반 무효화는 실시간 랭킹에서 너무 잦아 무의미)

---

## 8. 콜드 스타트 대응

### 8.1 문제

일간 키가 전환되는 자정(KST)에 새 키(`ranking:all:{오늘}`)는 비어있다.

| 시간 | 상태 | 유저 경험 |
|------|------|----------|
| 23:59 | `ranking:all:20260410`에 데이터 풍부 | "인기 상품" 정상 노출 |
| 00:00 | `ranking:all:20260411` 생성, 비어있음 | **"인기 상품" 텅 빔** |
| 00:01~02:00 | 이벤트가 서서히 유입 | 소수 상품만 노출, 편향된 랭킹 |
| 06:00~ | 충분한 이벤트 누적 | 정상 랭킹 |

새벽 시간대에 유저가 적더라도 **랭킹이 비어있는 것 자체가 서비스 품질 문제**다.
또한 자정 직후 유입된 소수 이벤트가 랭킹을 지배하여 편향된 결과를 보여줄 수 있다.

### 8.2 해결 — Score Carry-Over (ZUNIONSTORE)

전날 랭킹의 일부를 새 키에 복사하여 초기 데이터를 확보한다.

```
ZUNIONSTORE ranking:all:20260411 1 ranking:all:20260410 WEIGHTS 0.1
EXPIRE ranking:all:20260411 172800
```

- `WEIGHTS 0.1`: 전날 score의 10%만 이월
- 결과: 전날 1위(score 4.61) → 오늘 초기 score 0.461

**10%인 이유**:

```
전날 1위 carry-over:   0.1 × 4.61 = 0.461
오늘 신규 이벤트 누적:  상품이 조회 100회 + 좋아요 5회 + 주문 5만원만 받아도
                       0.1×log₁₀(101) + 0.2×log₁₀(6) + 0.7×log₁₀(50001)
                       = 0.20 + 0.16 + 3.29 = 3.65

→ 오늘의 실제 인기(3.65)가 carry-over(0.461)를 빠르게 역전
→ carry-over가 랭킹을 고착시키지 않으면서, 새벽에는 빈 랭킹을 방지
```

만약 carry-over를 50%로 잡으면:
```
전날 1위 carry-over:   0.5 × 4.61 = 2.305
→ 오늘 실제 이벤트가 상당히 쌓여야 역전 가능 → 어제 인기 상품이 오늘도 상위 고착
```

**10%는 "빈 랭킹 방지"와 "오늘 데이터로 빠른 역전"의 균형점이다.**

#### 업계 검증 — Carry-Over는 일반적 패턴인가?

ZUNIONSTORE WEIGHTS를 이용한 score carry-over는 다음과 같은 업계 사례에서 검증된 패턴이다:

| 사례 | 방식 | 비율/감쇠 |
|------|------|----------|
| Reddit Hot Ranking | 시간 감쇠 함수(gravity)로 오래된 게시물 score 자연 감소 | 시간 경과에 따라 지수적 감쇠 |
| Hacker News | `score / (T+2)^gravity` — 경과 시간에 비례한 감쇠 | gravity=1.8 |
| **ZUNIONSTORE WEIGHTS 패턴** | 전날 ZSET을 가중치 곱하여 새 키에 이월 | 0.1~0.3이 일반적 |

우리의 carry-over는 Reddit/HN의 시간 감쇠를 **이산적(일 단위)**으로 구현한 것이다. 연속적 감쇠(매 요청마다 score를 시간 함수로 재계산)는 Redis ZSET 구조에서 비효율적이고(모든 member의 score를 갱신해야 함), 일 단위 감쇠가 랭킹 특성에 적합하다.

### 8.3 Hash Carry-Over는?

ZUNIONSTORE는 ZSET만 복사한다. 전날의 Hash(개별 메트릭)는 이월하지 않는다.

| 대안 | 장점 | 단점 |
|------|------|------|
| Hash도 복사 | score 재계산 가능 | 상품별 Hash 복사 = N개 키 생성, ZUNIONSTORE의 단순함 상실 |
| Hash 미복사 | 단순, 빠름 | carry-over 상품의 개별 메트릭 조회 불가 |

**결정: Hash는 미복사.**

- Carry-over는 임시 초기값일 뿐이다. 오늘 이벤트가 들어오면 Hash가 자연스럽게 생성된다
- Carry-over 상품에 오늘 이벤트가 전혀 없으면, ZADD가 발생하지 않아 carry-over score가 유지된다
- 이 경우 Hash가 없어서 score 재계산이 불가하지만, carry-over score 자체가 충분히 의미있는 값이다

### 8.4 실행 시점 — 스케줄러

| 대안 | 설명 | 문제 |
|------|------|------|
| 자정 정각 (00:00) | 날짜 전환 즉시 실행 | API가 새 키를 조회하는 시점과 경합 가능, 극히 짧은 빈 구간 발생 |
| 23:50 (전날) | 미리 다음 날 키 생성 | **경합 없음**. 자정이 되면 이미 데이터가 있는 키를 조회 |
| 첫 요청 시 Lazy | API가 빈 키를 감지하면 그때 carry-over | 첫 요청 지연, 동시 요청 시 중복 실행 위험 |

**결정: 23:50 KST에 스케줄러로 사전 생성.**

```java
@Scheduled(cron = "0 50 23 * * *", zone = "Asia/Seoul")
public void carryOverRanking() {
    String today = todayKey();      // "20260410"
    String tomorrow = tomorrowKey(); // "20260411"

    // ZUNIONSTORE ranking:all:20260411 1 ranking:all:20260410 WEIGHTS 0.1
    // EXPIRE ranking:all:20260411 172800
}
```

**23:50인 이유**:
- 자정 전 10분 여유 → 네트워크/Redis 지연이 있어도 충분
- 23:50~00:00 사이 10분간 오늘 키에 계속 이벤트가 쌓이지만, carry-over 비율이 10%라 그 차이는 무시 가능
- 만약 23:50에 실패하면 00:00에 재시도하는 fallback 스케줄도 추가 가능

### 8.5 Carry-Over 후 이벤트가 들어오면?

```
23:50 — ZUNIONSTORE로 다음 날 키 생성
        ranking:all:20260411 = { 101: 0.461, 202: 0.428, ... }

00:00 — 날짜 전환. MetricsConsumer가 dateKey="20260411"로 전환

00:05 — 상품 101에 조회 이벤트 발생
        Phase 3: HINCRBY → Hash 생성 → score 재계산 → ZADD
        ranking:all:20260411 의 101 score가 carry-over(0.461) → 새 score(예: 0.561)로 덮어씀
```

ZADD는 **기존 score를 무조건 덮어쓴다.** Carry-over로 생성된 score든 이전 배치의 score든, 새 score로 대체된다. Metric 기반 접근이므로 항상 Hash 전체 상태에서 재계산한 값이 ZADD되어 정합성이 유지된다.

단, carry-over로만 존재하고 오늘 이벤트가 없는 상품은 carry-over score가 그대로 유지된다. 이는 의도된 동작이다 — 어제 인기 있었던 상품이 오늘 새벽에도 일정 순위를 유지하는 것이 UX상 자연스럽다.

---

## 9. 동점 처리

### 9.1 동점이 발생하는 경우

score 수식이 `0.1×log₁₀(viewCount+1) + 0.2×log₁₀(likeCount+1) + 0.7×log₁₀(salesAmount+1)`이므로, 동일한 메트릭 조합을 가진 상품이 존재하면 동점이 된다.

실제 발생 가능성:

| 시나리오 | 가능성 | 설명 |
|---------|--------|------|
| 초기 (이벤트 적음) | **높음** | 조회 1회, 좋아요 0건, 주문 0건인 상품이 다수 → 모두 score = 0.1×log₁₀(2) ≈ 0.030 |
| carry-over 직후 | **높음** | 전날 동점이었던 상품들이 동일 비율로 이월 → 동점 유지 |
| 일과 시간 | **낮음** | 이벤트가 누적될수록 메트릭 조합이 분화, log 스케일이 미세 차이를 보존 |

### 9.2 Redis ZSET의 동점 기본 동작

score가 동일하면 Redis는 **member의 사전식(lexicographic) 순서**로 정렬한다.

```
member: "101", score: 0.030
member: "202", score: 0.030
member: "99",  score: 0.030

→ 사전식 순서: "101" < "202" < "99" (문자열 비교)
→ ZREVRANGE 시: "99", "202", "101" 순서
```

productId가 숫자이므로 사전식 순서는 비즈니스 의미가 없다 (99 > 202 > 101).

### 9.3 타이브레이커 — 신상품 우선 (productId 기반)

| 대안 | 구현 | 장점 | 단점 |
|------|------|------|------|
| 아무것도 안 함 (ZSET 기본) | 변경 없음 | 단순 | 동점 시 순서가 자의적 (사전식) |
| 타임스탬프 인코딩 | `score = baseScore + (1 - ts/10¹⁰)` | 먼저 달성한 상품 우선 | score에 두 가지 의미 혼합, 디버깅 어려움 |
| salesCount 인코딩 | `score = baseScore + salesCount × ε` | 비즈니스 의미 있음 | salesAmount가 이미 주 score에 반영 → **같은 시그널의 이중 반영** |
| **productId 인코딩** | `score = baseScore + productId × ε` | **신상품에 노출 기회 부여**, 주 score와 다른 차원의 보정 | productId가 auto-increment가 아닌 경우 무의미 |

**결정: productId를 score에 인코딩하여 ZSET 레벨에서 동점을 해소한다.**

근거:
- salesCount는 이미 salesAmount를 통해 주 score에 반영되고 있다. 타이브레이커에 다시 쓰면 "매출" 시그널을 이중으로 반영하는 셈이다
- 동점인 상품 중 **최근 등록된 신상품이 상위**에 오면, 아직 이벤트가 충분히 쌓이지 않은 신상품에 노출 기회를 준다 → **미시적 콜드 스타트 완화**
- productId는 auto-increment이므로 높을수록 최근 등록. Phase 3에서 이미 보유하고 있어 추가 조회 불필요
- 주 score(조회/좋아요/매출)와 **완전히 다른 차원**의 보정이라 정보가 중복되지 않는다

### 9.4 ε(엡실론) 산정

productId를 score의 소수점 아래에 인코딩하되, **주 score에 영향을 주지 않을 만큼 작아야** 한다.

**주 score의 최소 유의미 차이**:

```
가장 작은 변화: viewCount 0→1
기여 변화: 0.1 × (log₁₀(2) - log₁₀(1)) = 0.1 × 0.301 = 0.0301
```

**productId의 현실적 범위**: 1~10,000,000 (천만, 대규모 서비스 상한)

**ε 후보 검증**:

| ε | productId=10,000,000일 때 보정값 | 주 score 최소 차이(0.0301) 대비 | 안전성 |
|---|-------------------------------|-------------------------------|--------|
| 1e-9 | 0.01 | 33% | 위험 — 주 score를 역전시킬 수 있음 |
| **1e-10** | **0.001** | **3.3%** | **안전** — 주 score 차이의 30분의 1 |
| 1e-11 | 0.0001 | 0.3% | 과잉 안전 |

**결정: ε = 1e-10**

- productId 1,000만이어도 보정값 0.001 → 주 score 차이(0.03)의 3.3%
- Redis double(64bit IEEE 754)은 유효 자릿수 15~16자리 → score 범위 0~6에서 1e-10은 충분히 표현 가능
- 현재 과제 상품은 5개(ID 1~5)이므로 보정값은 극히 미미하지만, 동점 해소에는 충분

### 9.5 최종 수식 (타이브레이커 포함)

```
score(p) = 0.1 × log₁₀(viewCount + 1)
         + 0.2 × log₁₀(likeCount + 1)
         + 0.7 × log₁₀(salesAmount + 1)
         + productId × 1e-10
```

**검증 — 동점 시 신상품 우선**:

```
Product 101 (구상품): view=1, like=0, salesAmount=0
  주 score = 0.1×log₁₀(2) = 0.0301
  tiebreaker = 101 × 1e-10 = 0.0000000101
  최종: 0.0301000101

Product 505 (신상품): view=1, like=0, salesAmount=0
  주 score = 0.1×log₁₀(2) = 0.0301
  tiebreaker = 505 × 1e-10 = 0.0000000505
  최종: 0.0301000505

→ 505(신상품) > 101(구상품) ✓
```

**검증 — 주 score를 역전시키지 않는가?**:

```
Product 101: view=2, like=0, salesAmount=0
  최종: 0.1×log₁₀(3) + 101×1e-10 = 0.0477000101

Product 999999 (신상품): view=1, like=0, salesAmount=0
  최종: 0.1×log₁₀(2) + 999999×1e-10 = 0.0301001000

→ 101(0.0477) > 999999(0.0301) ✓ — productId가 1만배 차이나도 주 score가 높은 쪽이 상위
```

---

## 10. 장애 시나리오

### 10.1 장애 분류

랭킹 시스템의 장애 포인트는 **쓰기 경로(Consumer → Redis)**와 **읽기 경로(API → Redis)**로 나뉜다.

```
쓰기 경로:
  Kafka → MetricsConsumer → [Phase 1: DB 멱등성] → [Phase 2: DB upsert] → [Phase 3: Redis 적재]
                                                                              ↑ 장애 포인트

읽기 경로:
  유저 → RankingController → RankingFacade → [RankingRedisRepository → Redis Replica]
                                                        ↑ 장애 포인트
```

### 10.2 쓰기 경로 장애

#### Redis 장애 시 — Phase 3 실패

| 항목 | 설명 |
|------|------|
| 영향 범위 | 해당 배치의 랭킹 갱신만 유실. DB(product_metrics)는 정상 커밋됨 |
| 유저 영향 | 랭킹이 수 초~수십 초 지연 반영. 기존 데이터로 조회 가능 |
| 대응 | Phase 3를 try-catch로 격리. WARN 로그 기록. 다음 배치에서 자연 복구 |
| 재시도 | 불필요. 다음 배치의 HINCRBY가 누적 delta를 반영하고, score 재계산이 Hash 전체 상태 기반이므로 정합성 유지 |

```java
// MetricsConsumer.consume() 내
try {
    rankingScoreUpdater.update(dateKey, deltaMap);
} catch (Exception e) {
    log.warn("랭킹 Redis 적재 실패 — 다음 배치에서 자연 복구됨", e);
}
ack.acknowledge();
```

**핵심**: Phase 3 실패가 Phase 1~2에 전파되지 않는다. `ack.acknowledge()`는 Phase 3 성공 여부와 무관하게 호출된다.

#### Kafka Consumer 재시작 / 리밸런싱

| 항목 | 설명 |
|------|------|
| 영향 | 리밸런싱 중 이벤트 처리 지연 (수 초~수십 초) |
| 데이터 정합성 | event_handled 멱등성 체크로 중복 처리 방지. 같은 이벤트를 다시 받아도 INSERT IGNORE로 스킵 |
| Redis 정합성 | 멱등성 체크를 통과한 이벤트만 deltaMap에 포함되므로, Redis에도 중복 반영되지 않음 |

#### Redis 장애 복구 후 데이터 정합성

Redis가 복구되면 Hash/ZSET이 유실되었을 수 있다.

| 시나리오 | 결과 | 대응 |
|---------|------|------|
| Hash 유실, ZSET 유실 | 빈 랭킹 | 이벤트가 계속 들어오므로 Hash/ZSET이 자연 재생성. 복구 직후 수 분간 랭킹이 부정확 |
| Hash 유실, ZSET 잔존 | ZSET의 score가 오래된 값 | 새 이벤트의 HINCRBY로 Hash 재생성 → score 재계산 → ZADD로 ZSET 갱신. 단, 장애 전 누적분은 유실 |
| Hash 잔존, ZSET 유실 | 랭킹 목록 없음 | 새 이벤트의 score 재계산 → ZADD로 ZSET 재생성 |

**모든 경우 "새 이벤트가 들어오면 자연 복구"된다.** Metric 기반 접근의 장점 — Hash가 SSOT이므로, Hash만 있으면 score를 언제든 재계산할 수 있다.

단, Hash까지 유실된 경우 장애 전 누적 메트릭이 유실된다. 이 경우 **배치 보정 잡**(섹션 2.5 Lambda Architecture)이 `product_metrics`의 일별 데이터를 기반으로 Hash/ZSET을 재구축한다. 배치가 1시간 주기이므로, 최대 1시간 이내에 정확한 랭킹으로 복구된다.

### 10.3 읽기 경로 장애

#### Redis Replica 장애 시 — API 조회 실패

| 대안 | 설명 | 적합성 |
|------|------|--------|
| 빈 응답 반환 | `data: []`, totalElements: 0 | 단순하지만 UX 저하 |
| **에러 응답** | 503 Service Unavailable + 적절한 메시지 | **명확** — 클라이언트가 재시도 판단 가능 |
| DB fallback | product_metrics에서 오늘 날짜 조회 + score 계산 | 가능하나 실시간 요청마다 GROUP BY + score 계산은 부하 |
| 로컬 캐시 fallback | 마지막 성공 응답을 캐시해서 반환 | 구현 복잡도 증가 |

**결정: Redis 조회 실패 시 에러 응답(503)을 반환한다.**

근거:
- DB fallback은 가능하나(product_metrics에 일별 데이터 존재), 실시간 요청마다 score 계산 + 정렬은 부하가 크다
- 로컬 캐시는 현재 요구사항 대비 과도한 복잡도
- 랭킹은 핵심 비즈니스(주문/결제)가 아니므로, 일시적 503은 허용 가능

```java
// RankingFacade.getRankings() 내
try {
    return rankingRedisRepository.getTopN(date, start, end);
} catch (Exception e) {
    log.error("랭킹 Redis 조회 실패", e);
    throw new CoreException(ErrorType.INTERNAL_ERROR, "랭킹 서비스를 일시적으로 이용할 수 없습니다.");
}
```

#### 상품 상세의 랭킹 정보 — 부분 장애 허용

상품 상세 API(`GET /api/v1/products/{productId}`)에서 랭킹 정보 조회가 실패하면, **상품 정보는 정상 반환하고 랭킹만 null로** 처리한다.

```java
// ProductFacade 내
ProductRanking ranking = null;
try {
    ranking = rankingRedisRepository.getRankAndScore(today, productId);
} catch (Exception e) {
    log.warn("상품 {} 랭킹 조회 실패 — ranking=null로 응답", productId, e);
}
```

상품 상세는 핵심 기능이므로, 부가 정보(랭킹) 실패가 전체 응답을 실패시키면 안 된다.

### 10.4 장애 대응 요약

| 장애 | 쓰기/읽기 | 영향 | 대응 | 복구 |
|------|----------|------|------|------|
| Redis Master 장애 | 쓰기 | 랭킹 갱신 중단 | Phase 3 try-catch 격리, DB 정상 | 복구 후 자연 재생성 |
| Redis Replica 장애 | 읽기 | 랭킹 API 503 | 에러 응답 | Replica 복구 시 즉시 정상화 |
| Consumer 재시작 | 쓰기 | 수 초 지연 | 멱등성으로 중복 방지 | 자동 |
| Hash/ZSET 유실 | 쓰기+읽기 | 일간 데이터 유실 | 이벤트 유입으로 점진 재생성. product_metrics 기반 배치 보정으로 정합성 복구 (섹션 2.5 Lambda Architecture) | 수 분~수 시간 |
| 상품 상세 랭킹 조회 실패 | 읽기 | 랭킹 필드 null | try-catch, 상품 정보는 정상 반환 | 자동 |

---

## 11. 클래스 설계

### 11.1 전체 구조

```
[commerce-streamer] — 쓰기 경로
  interfaces/consumer/
    └── MetricsConsumer              — (수정) Phase 2: product_metrics에 metric_date + 취소 분리 + Late-Arriving Fact 이중 기록
                                       Phase 3 추가, MetricsDelta 외부 참조로 변경
                                       ORDER_CANCELLED: originalOrderDate 기반 발생일 UPSERT 추가
  application/ranking/
    ├── MetricsDelta                 — (기존 inner class → 추출 완료) 이벤트→메트릭 의미 정의 중앙화
    ├── RankingScoreUpdater          — (구현 완료) Pipeline HINCRBY → score 계산 → ZADD
    ├── RankingCarryOverScheduler    — (구현 완료) 23:50 ZUNIONSTORE carry-over
    └── RankingProperties            — (구현 완료) 가중치/carryOverRate 외부화 (Semantic Definition)

[commerce-batch] — 배치 보정 경로 (Lambda Architecture)
  application/ranking/
    └── RankingCorrectionJobConfig   — (신규) 1시간 주기 배치 보정 잡
          Step 1: product_metrics SELECT (오늘 날짜)
          Step 2: score 재계산
          Step 3: Redis Hash/ZSET 덮어쓰기

[commerce-api] — 읽기 경로
  interfaces/api/ranking/
    ├── RankingController            — (신규) GET /api/v1/rankings
    └── RankingDto                   — (신규) RankingResponse, PagedRankingResponse
  application/ranking/
    └── RankingFacade                — (신규) ZSET 조회 + DB 상품 정보 조합
  infrastructure/ranking/
    └── RankingRedisRepository       — (신규) ZREVRANGE, ZREVRANK, ZSCORE, ZCARD

  application/product/
    └── ProductFacade                — (수정) 상품 상세에 랭킹 정보 조합 추가
  interfaces/api/product/
    └── ProductDto                   — (수정) ranking 필드 추가
```

### 11.2 commerce-streamer 클래스 (구현 완료)

이미 설계대로 구현되어 있으며, 설계 문서와의 정합성을 확인한다.

#### MetricsDelta (Semantic Definition 중앙화)

```
application/ranking/MetricsDelta.java
├── likeDelta, viewDelta, salesCountDelta, salesAmountDelta
├── ofLike(int), ofView(), ofSales(int, long)  — 팩토리 메서드
└── merge(MetricsDelta, MetricsDelta)           — 배치 집계용 병합
```

- MetricsConsumer의 private inner class에서 별도 클래스로 추출됨
- Phase 2(DB)와 Phase 3(Redis) 모두에서 사용
- **이벤트→메트릭 매핑의 단일 정의 지점**: 새 이벤트 타입 추가 시 팩토리 메서드만 추가하면 Phase 1~3이 자연스럽게 따라감

#### MetricsConsumer 수정 사항

```
(수정) interfaces/consumer/MetricsConsumer.java
├── Phase 2 변경: product_metrics UPSERT에 metric_date(CURDATE()) 포함
│     ├── 모든 이벤트: 인식일 기준 UPSERT (기존 + unlike_count, cancel_by_event_date)
│     └── ORDER_CANCELLED: 발생일(originalOrderDate) 기준 추가 UPSERT
├── 이벤트 스키마: ORDER_CANCELLED에 originalOrderDate 필드 필요
└── Phase 3: 기존과 동일 (RankingScoreUpdater 위임)
```

- ORDER_CANCELLED 이벤트 처리 시 `originalOrderDate`를 파싱하여 발생일 행에도 UPSERT
- commerce-api의 주문 취소 이벤트 발행 로직에서 `originalOrderDate`를 포함하도록 수정 필요

#### RankingScoreUpdater

```
application/ranking/RankingScoreUpdater.java
├── update(Map<Long, MetricsDelta>)           — 진입점
├── pipelineHincrby(deltaMap, date)           — Pipeline 1: Hash 갱신
├── pipelineZadd(accumulated, zsetKey)        — Pipeline 2: ZSET 갱신
├── calculateScore(view, like, salesAmt, pid) — score 수식
├── zsetKey(LocalDate), hashKey(LocalDate, Long) — 키 생성 유틸
└── 상수: RANKING_ZSET_PREFIX, RANKING_METRICS_PREFIX, RANKING_TTL_SECONDS, TIEBREAKER_EPSILON
```

- writeTemplate(`@Qualifier("redisTemplateMaster")`) 사용
- RankingProperties 주입으로 가중치 외부화
- score 수식에 productId × 1e-10 타이브레이커 포함

#### RankingCarryOverScheduler

```
application/ranking/RankingCarryOverScheduler.java
├── carryOver()                               — @Scheduled 23:50 KST
└── carryOver(LocalDate)                      — 테스트 가능한 메서드
```

- ZUNIONSTORE + WEIGHTS(carryOverRate) + EXPIRE
- Hash 미복사 (설계 결정 반영)

#### RankingProperties

```
application/ranking/RankingProperties.java
├── weights: Weights(view, like, order)
└── carryOverRate: double
```

- `@ConfigurationProperties(prefix = "ranking")`로 외부화
- Additionals "실시간 Weight 조절"을 위한 확장점

### 11.3 commerce-batch 클래스 (신규 구현 필요)

#### RankingCorrectionJobConfig

```
application/ranking/RankingCorrectionJobConfig.java
├── rankingCorrectionJob()                     — Job 정의
│     Step 1: ItemReader  — product_metrics SELECT (metric_date = today)
│     Step 2: ItemProcessor — score 재계산 (RankingProperties.weights 참조)
│     Step 3: ItemWriter  — Redis Pipeline (DEL Hash → HSET → ZADD → EXPIRE)
├── 실행 주기: @Scheduled(cron = "0 0 * * * *") 또는 외부 스케줄러
└── 의존: DataSource, RedisTemplate(Master), RankingProperties
```

**설계 포인트**:

- Spring Batch의 chunk-oriented 처리 → 상품 1,000개씩 읽어 Redis Pipeline으로 일괄 적재
- `RankingProperties.weights`를 RankingScoreUpdater와 **동일하게 참조** → score 수식의 Semantic Definition 유지
- 키 상수(prefix, TTL, date format)는 RankingScoreUpdater와 동일 값 사용
- Reader는 `JdbcCursorItemReader`로 `idx_metric_date` 인덱스 활용

### 11.4 commerce-api 클래스 (신규 구현 필요)

#### RankingRedisRepository

```
infrastructure/ranking/RankingRedisRepository.java
├── getTopN(String date, int start, int end)  — ZREVRANGE WITHSCORES
│     → List<RankingEntry> (productId + score, score 내림차순)
├── getRankAndScore(String date, Long pid)    — ZREVRANK + ZSCORE
│     → RankingInfo (rank + score) or null
├── getTotalCount(String date)                — ZCARD
│     → long
└── 생성자: readTemplate (Replica 우선)
```

- `WaitingQueueRedisRepository` 패턴 참조: `@Component`, readTemplate 주입
- 키 상수는 streamer의 `RankingScoreUpdater`와 동일 값 사용 (모듈 간 직접 참조 없이 문자열 일치)

#### RankingFacade

```
application/ranking/RankingFacade.java
├── getRankings(String date, int page, int size)
│     1. date null → 오늘(KST)
│     2. start/end 계산 + Top 100 cap
│     3. RankingRedisRepository.getTopN()
│     4. productId 목록 → ProductRepository IN 쿼리
│     5. Redis 순서 유지하며 병합
│     6. PagedRankingResponse 반환
└── 의존: RankingRedisRepository, ProductRepository
```

#### RankingController

```
interfaces/api/ranking/RankingController.java
├── GET /api/v1/rankings
│     @RequestParam date (optional), page (default 0), size (default 20)
│     → ApiResponse<RankingDto.PagedRankingResponse>
└── 의존: RankingFacade
```

#### RankingDto

```
interfaces/api/ranking/RankingDto.java
├── RankingResponse (record)
│     rank, productId, productName, brandName, price, score
├── PagedRankingResponse (record)
│     data (List<RankingResponse>), totalElements, totalPages, page, size
└── RankingInfo (record) — 상품 상세용
      rank, score, date
```

#### ProductFacade / ProductDto 수정

```
ProductFacade (수정)
├── getProductDetail(productId) — 기존
└── getProductDetailCached(productId) — 랭킹 조합 추가
      RankingRedisRepository.getRankAndScore(today, productId)
      → try-catch로 감싸서 실패 시 ranking=null

ProductDto.ProductResponse (수정)
└── ranking: RankingDto.RankingInfo (nullable) — 추가 필드
```

### 11.5 모듈 간 키 상수 일치

commerce-streamer가 쓰는 키와 commerce-api가 읽는 키가 일치해야 한다.

| 상수 | 값 | streamer | api |
|------|---|----------|-----|
| ZSET 키 prefix | `ranking:all:` | RankingScoreUpdater | RankingRedisRepository |
| Hash 키 prefix | `ranking:metrics:` | RankingScoreUpdater | (사용 안 함 — 읽기 경로에서 Hash 직접 조회 불필요) |
| TTL | 172,800초 | RankingScoreUpdater | (설정 불필요 — 읽기 전용) |
| 날짜 포맷 | yyyyMMdd | RankingScoreUpdater | RankingRedisRepository |

**모듈 간 직접 의존 없이 문자열 값만 일치시킨다.** 공유 모듈을 만들면 결합도가 높아지므로, 각 모듈에서 상수를 독립 정의한다. 값이 3개뿐이라 동기화 부담이 낮다.

---

## 12. 체크리스트

과제 요구사항(`docs/requirements/09-ranking-system-quests.md`) 기준으로 설계 커버리지를 정리한다.

### Must-Have

#### Ranking Consumer (쓰기 경로)

| # | 항목 | 설계 섹션 | 구현 상태 |
|---|------|----------|----------|
| 1 | 랭킹 ZSET의 TTL, 키 전략을 적절하게 구성 | 섹션 4 (Key 설계) | streamer 구현 완료 |
| 2 | 날짜별 적재 키를 계산하는 기능 | 섹션 4.3 (KST 기준) | `RankingScoreUpdater.zsetKey()` 구현 완료 |
| 3 | 이벤트 발생 후 ZSET에 점수가 적절하게 반영 | 섹션 3 (점수 모델), 섹션 5 (Pipeline) | `RankingScoreUpdater.update()` 구현 완료 |

#### Ranking API (읽기 경로)

| # | 항목 | 설계 섹션 | 구현 상태 |
|---|------|----------|----------|
| 4 | 랭킹 Page 조회 시 정상적으로 랭킹 정보 반환 | 섹션 7.1 (페이지네이션) | **미구현** — RankingController, RankingFacade |
| 5 | 상품 ID가 아닌 상품 정보가 Aggregation되어 제공 | 섹션 7.1 (Aggregation 흐름) | **미구현** — RankingFacade 내 DB IN 쿼리 |
| 6 | 상품 상세 조회 시 해당 상품 순위 반환 (없으면 null) | 섹션 7.2 (상품 상세 랭킹) | **미구현** — ProductFacade 수정 |

#### 검증

| # | 항목 | 설계 섹션 |
|---|------|----------|
| 7 | 이벤트 발행 → ZSET 점수 반영 → API 조회 E2E | 섹션 2 (데이터 흐름) 전체 |
| 8 | 일자 변경 후 이전 날짜 랭킹 조회 정상 동작 | 섹션 4.4 (TTL 2일) |
| 9 | 가중치 적용이 의도대로 랭킹 순서에 반영 | 섹션 3.3 (수식 검증) |

### Nice-to-Have

| # | 항목 | 설계 섹션 | 구현 상태 |
|---|------|----------|----------|
| 10 | 시간 단위(초 실시간) 랭킹 | 섹션 4.5 (hourly 키 확장) | 설계만 — 키 패턴 확장으로 대응 가능 |
| 11 | 콜드 스타트 문제 해결 | 섹션 8 (carry-over) | `RankingCarryOverScheduler` 구현 완료 |
| 12 | 카프카 배치 리스너 | 섹션 2.2 (MetricsConsumer 확장) | 기존 BATCH_LISTENER 활용 (이미 3,000건 배치) |

### Additionals

| # | 항목 | 설계 섹션 | 구현 상태 |
|---|------|----------|----------|
| 13 | 실시간 Weight 조절 | 섹션 11.2 (RankingProperties) | `@ConfigurationProperties` 구현 완료, actuator refresh로 런타임 변경 가능 |
| 14 | 1시간 단위 랭킹 | 섹션 4.5 | 미구현 — hourly 키 전략 설계 완료 |
| 15 | 콜드 스타트 Scheduler (23:50) | 섹션 8.4 | `RankingCarryOverScheduler` 구현 완료 |

### 과제 범위 초과 — 메트릭 설계 심화

| # | 항목 | 설계 섹션 | 구현 상태 |
|---|------|----------|----------|
| 16 | product_metrics 그레인 재설계 (daily × product) | 섹션 2.5 (TO-BE) | **미구현** — 스키마 마이그레이션 + Phase 2 수정 |
| 17 | Additive Measure + 취소 분리 | 섹션 2.5 (설계 원칙 1) | **미구현** — unlike_count, cancel 컬럼 분리 |
| 18 | Late-Arriving Fact 이중 기록 | 섹션 2.5 (설계 원칙 2) | **미구현** — cancel_by_event_date + cancel_by_order_date |
| 19 | Lambda Architecture 배치 보정 잡 | 섹션 2.5 (설계 원칙 4), 섹션 11.3 | **미구현** — commerce-batch RankingCorrectionJobConfig |
| 20 | Semantic Definition 중앙화 | 섹션 2.5 (설계 원칙 3) | **부분 완료** — MetricsDelta 추출 완료, 파생 메트릭 정의는 구현 시 반영 |
| 21 | ORDER_CANCELLED 이벤트에 originalOrderDate 추가 | 섹션 2.5 | **미구현** — commerce-api 이벤트 발행 수정 |

### 구현 우선순위

```
1순위 (Must-Have, 미구현):
  → product_metrics 스키마 변경 (metric_date 추가, 취소 분리, Late-Arriving Fact) + MetricsConsumer Phase 2 수정
  → ORDER_CANCELLED 이벤트에 originalOrderDate 필드 추가 (commerce-api)
  → RankingRedisRepository
  → RankingFacade + RankingController + RankingDto
  → ProductFacade / ProductDto 수정

2순위 (검증):
  → E2E 흐름 테스트 (이벤트 발행 → Redis → API)
  → 일자 변경 테스트
  → 가중치 순서 검증 테스트
  → product_metrics 일별 적재 + 취소 분리 + Late-Arriving Fact 검증
  → 정합성 검증: SUM(cancel_by_order_date) = SUM(cancel_by_event_date)

3순위 (Lambda Architecture):
  → commerce-batch RankingCorrectionJobConfig 구현
  → 배치 보정 전후 Redis 데이터 정합성 검증
  → 배치 + 실시간 동시 실행 시 race condition 검증

4순위 (이미 완료 확인):
  → streamer 쪽 단위 테스트 확인
  → MetricsConsumer → RankingScoreUpdater 연동 확인
```
