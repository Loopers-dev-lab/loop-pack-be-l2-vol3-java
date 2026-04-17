# 이커머스 랭킹 배치 설계기 — 일간/주간/월간 집계가 어떻게 달라지나

---

### TL;DR

> 같은 데이터, 같은 Score 공식인데 시간 윈도우만 달라도 1위가 바뀐다. 10만 상품 × 30일(300만 행) 에 대한 테스트를 해보니 주간-월간 간 TOP100에 차이들이 보였다. 이 글은 "주간/월간 인기상품 TOP100을 MV로 관리하는 이커머스 설계" 경험을 바탕으로, 결과의 차이를 만드는 설계 판단(Score 방식), 차이가 정확하도록 보장하는 판단(전체 재계산), 차이를 안정적으로 생산하는 판단(Chunk + Partitioning)을 기록한다.

---

## 1. 설계 배경

UX를 고려하는 대규모 이커머스에서 "인기 상품 TOP 100"은 단순한 조회가 아닐 것이다. 조회수, 좋아요, 매출, 취소를 조합한 Score 계산, 일간/주간/월간이라는 시간 윈도우, 실시간과 배치라는 이중 경로까지 고려해야 될 것이다.

```
[실시간 경로]  Kafka → Redis ZSET  →  daily 랭킹 (빠르지만 근사치)
[배치 경로]    DB 원장 → Spring Batch → MV 테이블 → weekly/monthly 랭킹 (느리지만 정확)
```

이미 Redis로 일간/주간/월간 랭킹을 제공하고 있었다. 그런데 왜 MV 테이블을 또 만드는가?

Redis의 주간/월간 랭킹은 일별 score를 합산하거나 지수 감쇠(`daily × 0.97^i`)를 적용한 **근사치**다. `log₁₀`의 비선형성 때문에 "일별 score의 합 ≠ 기간 메트릭 합산 후의 score"가 된다. 이 차이가 순위를 바꾼다. MV를 활용해서 DB 원장에서 기간 전체를 직접 집계하여 **정확한 기간 랭킹**을 제공하려 고민해봤다.

두 방식의 TOP100 상품은 차이가 있다. 그렇다면 일간/주간/월간 집계는 어떻게, 왜 달라질까?

---

## 2. 집계가 달라지는 구조

### 2.1 차이를 만드는 판단 — "주간/월간 인기상품"의 산정 기준은 '총 판매량'일까?, '최근 인기'일까? 아니면 '전시기간에 의한 누적을 보정한 판매량'일까?

일간/주간/월간 집계에 별반 차이가 없다면 사용자의 UX경험이 나쁠 것이다. 어느정도 이상의 결과 차이를 보이려면 Score 계산 방식이 그 차이를 허용해야 한다. 무엇을 기준으로 Score 집계해서 기간별 랭킹 차이를 만들어볼까?

#### 검토한 MV Score 계산 방식

| 방식 | 계산 | 특성 |
|------|------|------|
| **균등 합산** | `score = f(SUM(30일 메트릭))` | 30일 전이나 오늘이나 동등한 가중치. "기간 총 실적" |
| **지수 감쇠** | `monthly = Σ(daily × 0.97^i)` | 최근 데이터에 높은 가중치. 반감기 약 23일 |
| **일평균** | `score = f(SUM / 전시일수)` | 전시 기간에 관계없이 "일당 성과" |

#### 결정: 균등 합산

**회사 MD분에게 질문했다. "'이번 달 인기상품'으로 전시되는 상품은 어떤 상품이어야 할까요?"에 대해서 "무조건 '총 판매량'이 기준이다."라는 답변을 얻었다.** 비즈니스적으로 가장 의미있고, 소비자가 기대하는 바에도 가장 정직하게 부합하는 기준이라는 게 그 이유다.

두 시스템의 역할 분담은 이렇게 된다:

| | Redis (실시간 경로) | MV (배치 경로) |
|------|---------------------|-------------------|
| **비즈니스 의미** | "지금 뜨는 상품" (트렌드) | "이번 달 베스트셀러" (누적 성과) |
| **Score 방식** | 지수 감쇠 | 균등 합산 |
| **소비자 시나리오** | 메인 페이지 실시간 인기 | 카테고리별 베스트, 기간별 랭킹 |

균등 합산은 전시 기간이 긴 상품이 유리하다는 트레이드오프가 있다. 지수 감쇠로 이를 희석할 수 있지만, 그러면 오히려 판매량이 훨씬 떨어지지만 최근에 인기있던 상품이 스테디셀러보다 우위에 서게 될 것이고, 결정적으로 일간 집계 결과와 차이가 적어져서 UX경험 측면에서 좋지 않을 것이라고 판단했다.

#### 테스트 결과

10만 상품 × 30일(300만 행) 테스트에서, 운영 환경에서 관찰되는 6가지 트렌드 패턴을 시딩했다:

| 패턴 | 비율 | 특징 |
|------|------|------|
| 급상승 | 5% | 과거 23일 미미 → 최근 7일 폭발 |
| 장기강자 | 10% | 30일 꾸준히 높음 |
| 하락추세 | 5% | 과거 높음 → 최근 급락 |
| 바이럴 | 2% | 오늘 하루만 폭발 |
| 취소높음 | 3% | 매출 높지만 취소 50~70% |
| 일반 | 75% | 보통 수준 |

균등 합산을 적용한 결과:

- **weekly 1위**: product_5000 (급상승 — 최근 7일 폭발)
- **monthly 1위**: product_15000 (장기강자 — 30일 꾸준히 높음)

같은 Score 공식인데 시간 윈도우가 달라지니 1위도 완전히 달라졌다. 구현한 API의 결과 역시 아래와 같다:

| 순위 | 일간 (Redis) | 주간 (MV) | 월간 (MV) |
|:----:|-------------|-----------|-----------|
| 1 | 아디다스 캠퍼스 올리브 **(바이럴)** | 나이키 에어리프트 카키 **(급상승)** | 반스 슬립온 올리브 **(장기강자)** |
| 2 | 살로몬 아웃펄스 네이비 **(바이럴)** | 컨버스 런스타하이크 그레이 **(급상승)** | 스투시 카고바지 화이트 **(장기강자)** |
| 3 | 뉴발란스 530 올리브 **(바이럴)** | 스투시 월드투어후디 카키 **(급상승)** | 리복 클럽C85 인디고 **(장기강자)** |

반대 방향도 있었다. 하락추세 상품(메종키츠네)이 **월간 1위인데 일간/주간 19위**였다 — 과거 23일의 실적이 월간에는 남지만 최근 급락은 즉시 반영됐다.

**같은 공식이라도 시간 윈도우에 따라 집계 대상이 달라지고, 그 결과 "인기 상품"의 순위가 완전히 바뀐다.** 하나의 랭킹만 제공하면 어떤 관점은 누락된다. 일간만 보여주면 장기 스테디셀러가 사라지고, 월간만 보여주면 바이럴 상품이 보이지 않는다. 이렇게 기간별 랭킹의 차이를 확인했다.

---

### 2.2 차이가 정확하려면 — 매번 원장에서 재계산하면 비효율적인가?

시간 윈도우별로 다른 랭킹을 보여주는 것은 2.1에서 가능해졌다. 그런데 그 차이가 **정확한** 차이인가? MV는 매일 원장(product_metrics)에서 7일/30일 전체를 GROUP BY로 새로 집계한 데이터를 가지도록 했다. 그런데 증분 계산(어제 결과 - 가장 오래된 날 + 오늘)을 이용하면 데이터 처리량을 93%(월간 기준) 줄일 수 있다.

#### 고민

월간 기준 30일분을 매일 재계산하는 것은 29/30 = 97%의 데이터를 중복 처리하는 것처럼 보인다. "약간 정도는 틀어져도 사용자가 모를 텐데, 효율성과 장애 대응 관점에서 증분이 낫지 않을까?"라는 고민을 꽤나 반복했다.

#### 증분 계산이 깨지는 이유: Late-Arriving Fact

이커머스에서 주문 취소/환불은 원주문과 다른 날에 발생한다:

```
4/10: 상품 A 주문 100건 (1,000만원)
4/15: 그 중 30건 취소 → product_metrics 4/10 행의 cancel_by_order_date 갱신

증분 계산: 4/10의 값은 이미 어제 MV에 반영됨 → 사후 변경을 감지 못함
전체 재계산: 4/10~4/16 전체를 다시 읽으므로 → 변경된 값이 자동 반영
```

증분 계산은 **"과거 데이터가 불변"이라는 전제가 필요하다.** `cancel_by_order_date`가 과거 행을 사후 갱신하므로 이 전제가 깨진다.

| 시나리오 | 전체 재계산 | 증분 계산 |
|---------|-----------|----------|
| 정상 주문 | 정확 | 정확 |
| 지연 취소 (주문 후 며칠 뒤) | 자동 반영 | 원주문 날짜 변경 감지 못함 |
| 운영팀 데이터 보정 | 다음 배치 자동 반영 | 전체 재계산을 별도 실행해야 함 |
| 오류 전파 | 없음 (매번 독립 계산) | 어제 MV가 틀리면 오늘도 틀림 |

#### 결정: 전체 재계산

성능 차이(Partitioning 4 Worker 기준 ~10초 vs ~3초)는 **1일 1회 배치에서 운영 영향이 없다.**고 판단했다. 증분이 유리해지는 전환점은 배치 주기가 5분 이하로 빈번해질 때일 것이다.

#### 테스트 결과

E2E 테스트 시나리오 중 #7(취소 반영 테스트)에서 이 판단을 검증했다:

```
상품 A: 매출 100만 / 취소 0     → 순매출 100만
상품 B: 매출 200만 / 취소 150만  → 순매출 50만

결과: 상품 A가 1위 (총매출이 아닌 순매출 기준)
```

전체 재계산 덕분에, 취소가 나중에 발생해도 다음 배치에서 자동으로 반영된다. 증분이었다면 원주문 날짜의 취소 변경을 놓쳤을 것이다.

MV의 존재 이유에는 "Redis 근사치와 다른 정확한 기간 집계"도 있다고 생각하기 때문에 과거 데이터 변경을 반영하지 못하는 증분 방식을 쓰면 MV의 정확성이 약해지므로 MV를 도입하는 의의가 약해진다고 생각했다.

---

### 2.3 차이를 안정적으로 생산하려면 — Chunk vs Tasklet: 90개 실무 Job이 알려준 것

Score 방식과 전체 재계산으로 정확한 기간별 랭킹 차이를 만들 수 있게 되었다. 이제 이것을 매일 안정적으로 생산하는 처리 모델을 선택해야 한다.

#### 판단

이 작업은 Tasklet으로도 가능하다. `INSERT INTO...SELECT + RANK() OVER + LIMIT 100`으로 SQL 한 문장이면 끝이고, 네트워크 왕복도 0이다.

우리팀에서 사용하는 실무 배치 애플리케이션 2개(총 90개 Job)를 분석했더니 통계/집계 Job의 대다수가 Tasklet이었다. 처음에는 "Tasklet이 보편적"인 줄 알았는데, 두 앱 모두 MyBatis + SQL 중심 아키텍처여서 Tasklet(`INSERT INTO...SELECT`)이 자연스러운 선택이었던 것 같다. 하지만 Spring Batch 프레임워크 자체는 Chunk를 중심으로 설계되어 있고, retry/skip/restart 등 운영 기능이 Chunk에만 제공된다.

#### Tasklet이 맞는 조건

팀 배치의 Job 분석에서 도출한 Tasklet 조건은 세 가지다:

| 조건 | 설명 |
|------|------|
| SQL 한 문장으로 완결 | Java 변환이 전혀 없고 DB → DB 이동 |
| retry/skip이 불필요 | 실패 시 전체 재실행해도 수초 내 완료 |
| 중간 상태가 없음 | 처리 중 실패해도 "부분 완료" 상태가 의미 없음 |

사실 이번 설계에서의 MV에 TOP 100 적재는 세 조건을 모두 충족한다. 그런데도 Chunk를 선택한 이유가 있다.

#### 팀의 배치 Job 운영에서 아쉬웠던 점

```
팀 운영 Job(90개)의 기능 사용 현황:

  faultTolerant()          → 0개
  retry() / retryLimit     → 0개
  skip() / skipLimit       → 0개
  ItemReadListener         → 0개
  ChunkListener            → 0개
  allowStartIfComplete     → 0개
```

**90개 Job 중 단 하나도 retry, skip, restart를 사용하지 않는다.** 이것은 "안 써도 된다"가 아니라, **"1건의 일시적 DB 에러가 전체 배치를 실패시키는 구조로 운영하고 있다"**는 뜻이다. 야간 배치가 데드락으로 실패하면 아침에 출근해서 수동 재실행해야 한다.(지난주에도 그랬다..) `faultTolerant().retry(3)`를 걸어두면 자동으로 복구됐을 에러다.

#### 결정: Chunk

Chunk를 선택하면 Spring Batch의 운영 기능을 활용할 수 있다:

- **`faultTolerant + retry + ExponentialBackOffPolicy`**: 일시적 DB 에러(데드락, 커넥션 타임아웃) 시 100ms → 200ms → 400ms 간격으로 자동 재시도
- **`StepExecution` 자동 기록**: 각 Worker별 readCount, writeCount를 Spring Batch가 추적
- **`StepMonitorListener`**: 실패 시 알림

100건에 대한 네트워크 왕복 비용(< 1ms)보다 이 운영 기능의 가치가 크다고 판단했다. 현재 팀에서 안 쓰니까 안 써도 되는 것이 아니라, 프레임워크가 제공하는 운영 기능을 활용하여 야간 배치의 자동 복구 가능성을 높이고 싶었다.

---

## 3. 차이를 빠르게 만들려면 — 3-Step Chunk Job

Score 방식(2.1)이 차이를 만들고, 전체 재계산(2.2)이 정확성을 보장하고, Chunk(2.3)가 안정성을 제공한다. 남은 문제는 **속도**다. 300만 행(10만 상품 × 30일)을 매일 전체 재계산하면서도, 배치가 운영 부담이 되지 않을 처리 구조를 고민했다.

### 배치 구조

```
Step 1: CleanupTasklet
  └─ 기존 period_key 데이터 삭제 (멱등성 보장)
  └─ 3일 이전 데이터 자동 퍼지

Step 2: Partitioned Aggregate (병렬)
  └─ product_id MIN~MAX 범위를 4파티션으로 분할
  └─ Reader(SQL): 파티션별 GROUP BY 집계 (view, like, net_sales)
  └─ Processor(Java): ScoreFormula.calculate()로 Score 계산
  └─ Writer: staging 테이블 적재

Step 3: Merge
  └─ staging에서 Global TOP 100 추출 → MV 테이블 적재
  └─ ROW_NUMBER() OVER (ORDER BY score DESC) LIMIT 100
```

핵심은 Map-Reduce 패턴이다. 각 파티션(Map)이 독립적으로 메트릭을 집계(Reader SQL)하고 Score를 계산(Processor, `ScoreFormula`)한 뒤, Merge 단계(Reduce)에서 전체 순위를 매긴다.

### GROUP BY 집계에서 PagingReader가 위험한 이유

Reader로 `JdbcCursorItemReader`를 선택했다. 이유는 GROUP BY 집계 쿼리에서 `JdbcPagingItemReader`가 치명적이라고 생각했기 때문이다.

PagingReader는 페이지마다 **독립된 쿼리를 재실행**한다. 단순 WHERE 쿼리에서는 문제없지만, GROUP BY가 포함되면 매 페이지마다 전체 데이터를 다시 집계한다:

```
CursorReader:
  GROUP BY 3,000만 행 → 1번 실행 → 결과 스트리밍
  총 집계 실행: 1회

PagingReader (pageSize=1000, 상품 100만 건 = 1,000페이지):
  페이지 1: GROUP BY 3,000만 행 → 정렬 → OFFSET 0 LIMIT 1000
  페이지 2: GROUP BY 3,000만 행 → 정렬 → OFFSET 1000 LIMIT 1000
  ...
  총 집계 실행: 1,000회
```

그런데 CursorReader는 하나의 ResultSet을 열어두고 `next()`로 이동하는 구조여서 **멀티스레드에서 사용할 수 없다.** 두 스레드가 동시에 `next()`를 호출하면 커서가 밀리면서 데이터가 누락될 수 있기 때문이다.

### Partitioning으로 두 가지를 모두 해결

CursorReader의 장점(GROUP BY 1회 실행)을 유지하면서 멀티스레드 한계를 극복하려면, **데이터를 범위로 분할하여 각 Worker가 독립 CursorReader를 갖도록** 하면 된다. Spring Batch 공식 문서는 이 패턴을 "IO-intensive Step"에 적합한 스케일링 전략으로 설명한다:

> "The workers in this picture are all identical instances of a `Step`, which could in fact take the place of the manager, resulting in the same outcome for the `Job`."
> — [Spring Batch Scalability](https://docs.spring.io/spring-batch/reference/scalability.html)

```
Partitioner: product_id MIN~MAX → 4개 범위로 분할

  Worker 1: id 1~25,000      → 독립 CursorReader, 독립 DB 커넥션
  Worker 2: id 25,001~50,000  → 독립 CursorReader, 독립 DB 커넥션
  Worker 3: id 50,001~75,000  → 독립 CursorReader, 독립 DB 커넥션
  Worker 4: id 75,001~100,000 → 독립 CursorReader, 독립 DB 커넥션
```

이 범위 분할 로직은 Spring Batch 공식 샘플의 [`ColumnRangePartitioner`](https://github.com/SpringOne2GX-2014/spring-batch-performance-tuning)와 동일한 패턴을 적용했다:

```java
// Spring Batch 공식 샘플 — ColumnRangePartitioner.partition()
int min = jdbcTemplate.queryForObject("SELECT MIN(" + column + ") from " + table, Integer.class);
int max = jdbcTemplate.queryForObject("SELECT MAX(" + column + ") from " + table, Integer.class);
int targetSize = (max - min) / gridSize;

while (start <= max) {
    ExecutionContext value = new ExecutionContext();
    value.putInt("minValue", start);
    value.putInt("maxValue", end);
    result.put("partition" + number, value);
    start += targetSize;
    end += targetSize;
}
```

`createPartitioner`를 두어 `SELECT DISTINCT product_id WHERE metric_date BETWEEN ...`로 실제 메트릭이 존재하는 상품 ID 목록을 가져온 뒤, gridSize로 균등 분할하여 `ExecutionContext`에 담았다. 공식 샘플이 MIN/MAX 산술 분할을 기본 패턴으로 제시하고 있는데, 나는 DISTINCT 목록 기반 분할로 **메트릭이 없는 빈 구간이 파티션에 포함되지 않도록** 했다.

각 Worker가 자기 범위의 GROUP BY만 실행하므로 ResultSet 공유 문제가 없다. 결과는 staging 테이블에 모이고, mergeStep에서 Global TOP 100을 추출한다.

### Score 공식의 중앙화

Processor에서 사용하는 Score 공식(`LOG10 + 가중치`)은 원래 Streamer, Batch Correction, MV Job SQL, API Drift Scheduler 4곳에 분산되어 있었다. MV Job을 추가하면서 5번째 복사본이 생기는 시점에서 `ScoreFormula`(modules/jpa)로 중앙화했다. 가중치도 `ScoreFormula.Weights` record로 타입을 통일하고 `application.yml`에서 주입한다. 공식이 한 곳에만 존재하므로 변경 시 누락이 구조적으로 불가능해졌다.

### 벤치마크: gridSize=1 vs gridSize=4

10만 상품 × 30일(300만 행)에서 `ReflectionTestUtils`로 gridSize만 바꿔서 같은 데이터를 weekly/monthly 각 2회 실행한 결과:

| 구성 | weekly (7일, 70만행) | monthly (30일, 300만행) |
|------|---------------------|------------------------|
| gridSize=1 (단일 스레드) | **3,691ms** | **3,842ms** |
| gridSize=4 (4 Partition 병렬) | **1,746ms** | **2,188ms** |
| **향상률** | **2.1x** | **1.8x** |

이 표는 두 방향으로 읽을 수 있다.

#### 세로로 읽기 — "병렬화하면 얼마나 빨라지나?"

같은 scope에서 gridSize를 1→4로 올리면:

- **weekly**: 3,691ms → 1,746ms = **2.1x 향상**
- **monthly**: 3,842ms → 2,188ms = **1.8x 향상**

이론적 상한은 4x지만, Amdahl's Law에 의해 병렬화할 수 없는 직렬 구간(Partitioner의 `SELECT DISTINCT product_id`, mergeStep의 `ROW_NUMBER() OVER`, JobRepository 메타데이터 저장)이 상한을 낮춘다. 데이터가 많은 monthly에서 향상률이 더 떨어지는 이유는 아래에서 설명한다.

그래도 2.1x/1.8x는 의미 있다. 1일 1회 배치에서 절대적 차이는 크지 않지만, 데이터가 10배(100만 상품)로 늘어나면 37초 vs 18초(weekly), 38초 vs 22초(monthly)로 벌어진다. 병렬화의 효과는 규모에 비례할 것이기 때문이다.

#### 가로로 읽기 — "데이터가 4배면 얼마나 더 느린가?"

같은 gridSize에서 weekly(70만행)→monthly(300만행)로 데이터가 4배 늘어나면:

- **gridSize=1**: 3,691ms → 3,842ms = **+4%** (+151ms)
- **gridSize=4**: 1,746ms → 2,188ms = **+25%** (+442ms)

데이터가 4배인데 4배 느려지지 않는 이유는, Reader SQL의 GROUP BY가 데이터 볼륨을 흡수하기 때문이다.

```
weekly:  70만 행  ──GROUP BY──→ 10만 행 (상품별 집계)
monthly: 300만 행 ──GROUP BY──→ 10만 행 (상품별 집계)
```

GROUP BY를 통과하면 scope과 무관하게 **동일한 10만 건**이 파이프라인에 흐른다. Processor(`ScoreFormula`), Writer(staging INSERT), Merge(TOP 100 추출)는 모두 10만 건을 처리하므로 데이터 볼륨에 영향받지 않는다. 차이가 발생하는 유일한 구간은 Reader의 DB 스캔이다.

그런데 같은 GROUP BY 구조인데도, gridSize=1에서는 +4%이고 gridSize=4에서는 +25%로 **격차가 6배 벌어진다.** 병렬화가 이 격차를 줄여야 할 것 같지만 오히려 키운다. 이유는 IO 경합이다:

- **gridSize=1**: Worker 1개가 DB를 혼자 쓴다. 70만이든 300만이든 순차 스캔이라 경합이 없다.
- **gridSize=4**: Worker 4개가 동시에 같은 MySQL 인스턴스에 접근한다. weekly(각 17.5만행)는 buffer pool 256MB로 커버되지만, monthly(각 75만행)는 buffer pool 경합 + 디스크 IO 경합이 발생한다.

프로덕션 DB(buffer pool 수 GB 이상)에서는 300만 행(~450MB)이 메모리에 올라가므로 이 경합이 줄어들 것이다. gridSize=4의 +25%가 gridSize=1의 +4%에 수렴할 것으로 예상되며, gridSize=1도 디스크 IO가 사라지면서 +1~2%(순수 CPU 비용)로 줄어들 것으로 보인다.

유사한 패턴을 보이는 다른 사례가 있다. [prostars.net의 Partitioner 성능 측정](https://prostars.net/357)에서:

| Partition | 소요 시간 | 향상률 |
|-----------|----------|--------|
| 1 | 30s 809ms | — |
| 5 | 17s 319ms | **1.8x** |
| 10 | 17s 529ms | 1.8x |
| 15 | 17s 529ms | 1.8x |

> "파티션을 크게 설정한다고 무조건 성능이 좋아지는 것은 아니다"

partition=5 이후 향상률이 정체되는 것은 이번 구현에서 겪은 2.1x(gridSize=4)와 일맥상통한다. Amdahl's Law에 의해 직렬 구간이 병목이 되면 Worker를 아무리 늘려도 한계가 있다. 또한 thread pool size=1로 제한하면 partition=5에서도 **2분 15초**로 급격히 느려지는데, Partitioning은 스레드 풀과 함께 써야 의미가 있다는 것을 보여준다.

---

## 4. 시행착오

참고적으로 기술하자면..
### `@SpringBatchTest`가 private 메서드를 몰래 실행한다

```
No matching arguments found for method: runJob
```

`@SpringBatchTest`의 `JobScopeTestExecutionListener`는 테스트 클래스의 **모든 메서드**를 `getDeclaredMethods()`로 스캔한다. `JobExecution`을 반환하는 메서드를 찾으면 인자 없이 호출을 시도한다.

테스트 헬퍼 메서드 `private JobExecution runJob(String scope)`가 탐지 대상이 되어 실패했다. 반환 타입을 `BatchStatus`로 변경하면 스캔 대상에서 제외된다. 공식 문서에는 이 동작이 기술되어 있지 않다.

---

## 5. 운영 환경에서 적용할 조정

### gridSize 동적 조정

현재 gridSize를 4로 고정했지만, 실무에서는 커넥션 풀 크기와 CPU 코어 수에 연동해서 설정해야 하겠다. 배치 전용 DataSource의 커넥션 풀이 10이면 gridSize를 8 이상으로 잡으면 커넥션 고갈이 발생할 것이다. 그래서 `@Value`로 외부화 해두었으므로 프로파일별 설정으로 대응 가능하면 되겠다.

### 스테이징 테이블의 비용

상품 100만 개면 스테이징에 100만 행이 적재된다. mergeStep에서 TOP 100만 추출하고 나머지는 cleanup에서 삭제하지만, 이 중간 저장 비용이 Partitioning의 병렬 처리 이점을 상쇄하면 어쩌나 하는 고민이 있다. 처리 속도뿐 아니라 디스크 I/O, 트랜잭션 로그 크기도 고려해야 할 것이다.

---

## 6. 회고

현재 이커머스개발팀에서 근무하고 있지만 담당 파트만 작업하다보니 랭킹까지 고민한 적이 없었다.
'MD가 원하는 랭킹이 무엇인지' 비즈니스적 의미를 살펴보는 것을 시작으로, 정확하고 안정적인데 빠르게 작동하는 랭킹 집계 시스템을 설계해보는 것을 목표로 삼았다.

이번에 이커머스 랭킹을 설계해보니 "어떤 시간 윈도우로 보느냐"에 결과가 완전히 달라지다보니 현실에서 어떤 흐름으로 상품의 랭킹이 만들어질지를 생각해봤다. 오늘 SNS에서 주목받는 상품, 이번 주 꾸준히 팔린 상품, 한 달간 스테디셀러인 상품은 모두 "인기 상품"이지만, 하나의 랭킹으로 세 관점을 모두 담기 어려웠다.

scope별로 데이터 소스를 분리한 이유도 여기에 있다. 일간 랭킹은 Kafka → Redis의 실시간 경로(+ Batch Correction 보정)로, 주간/월간 랭킹은 DB 원장 기반 MV 배치로 각각 담당한다. **두 경로의 목적은 같은 데이터로 다른 관점을 제공하는 것이다.** Redis 경로는 지수 감쇠로 "지금 뜨는 상품"을, MV 배치 경로는 균등 합산으로 "기간 동안에 인기가 누적된 상품"을 각각 담당하는 셈이다. 두 경로를 서로 다르게 설계한 의도가 실제로 동작하는지를 테스트를 통해서 확인했다.

이번 설계를 고민하면서 다른 사례들을 찾아보는 것이 흥미로웠다. 이 글에서 다룬 "파티셔닝 → 병렬 집계 → merge"라는 Map-Reduce 패턴은 규모가 다른 시스템에서도 반복적으로 등장한다:

- [**Netflix Distributed Counter**](https://netflixtechblog.com/netflixs-distributed-counter-abstraction-8d0c45eb66b2): 시간 기반 파티셔닝 + Rollup 병렬 집계 → merge. *"A background rollup process continuously aggregates these events using time-based windows, storing intermediate counts in a persistent store."* 75K RPS, single-digit ms 레이턴시를 이 구조로 달성한다. 이 프로젝트에 적용한 3-Step(Cleanup → Partitioned Aggregate → Merge)과 동일한 구조이다.

- [**Shopify BFCM Live Map**](https://shopify.engineering/bfcm-live-map-2021-apache-flink-redesign): 텀블링 윈도우 5분 간격 TOP 500 집계. *"Redis would quickly become a bottleneck due to the increase in the number of published messages and subscribers."* BFCM 피크 **초당 100만 체크아웃 이벤트**를 처리하면서 Redis 병목을 Flink로 해소했다. "실시간 경로의 한계를 배치/스트림 집계로 보완한다"는 점에서 실시간 + 배치 이중 경로를 설계한 것과 같은 맥락을 보여준다.

"소프트웨어는 수학이 아니라서 정답이 없다." 이 점이 설계하는데 있어서 가장 어려웠다. 왜냐면 "정답을 찾는 것"이 아니라 **"선택한/선택안한 근거를 납득할 수 있게 정리하는 것"**이 필요했기 때문이다. 일례로 균등 합산을 선택하면서 지수 감쇠의 장점을 이해했고, 전체 재계산을 선택하면서 증분의 효율성을 인정했고, Chunk를 선택하면서 Tasklet의 단순함을 인정하는 과정이 설계 판단이었다. 어느 쪽이 "더 좋다"가 아니라 **"지금 상황에서 왜 이쪽인가"**를 설명하고 싶어서 고민하는 게 즐거웠다.
