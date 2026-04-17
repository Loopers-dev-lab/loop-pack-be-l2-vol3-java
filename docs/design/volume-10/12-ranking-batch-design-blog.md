# 이커머스 랭킹 배치 설계기 — 일간/주간/월간 집계가 어떻게 달라지나

---

## TL;DR

> 같은 데이터, 같은 Score 공식인데 시간 윈도우만 달라도 1위가 바뀐다. 10만 상품 × 300만 행 테스트에서 weekly 1위와 monthly 1위가 완전히 다른 상품이었다. 이 글은 "일간/주간/월간 집계가 어떻게 달라지나"라는 질문에서 출발하여, 그 차이를 만들어내는 설계 판단(Score 방식), 차이가 정확하도록 보장하는 판단(전체 재계산), 차이를 안정적으로 생산하는 판단(Chunk + Partitioning)을 기록한다.

---

## 1. 이 글의 맥락

쿠팡, 무신사 같은 이커머스에서 "인기 상품 TOP 100"은 단순한 조회가 아니다. 조회수, 좋아요, 매출, 취소를 조합한 Score 계산, 일간/주간/월간이라는 시간 윈도우, 실시간과 배치라는 이중 경로가 얽혀 있다.

```
[실시간 경로]  Kafka → Redis ZSET  →  daily 랭킹 (빠르지만 근사치)
[배치 경로]    DB 원장 → Spring Batch → MV 테이블 → weekly/monthly 랭킹 (느리지만 정확)
```

이미 Round 9에서 Redis로 일간/주간/월간 랭킹을 제공하고 있었다. 그런데 왜 MV 테이블을 또 만드는가?

Redis의 주간/월간 랭킹은 일별 score를 합산하거나 지수 감쇠(`daily × 0.97^i`)를 적용한 **근사치**다. `log₁₀`의 비선형성 때문에 "일별 score의 합 ≠ 기간 메트릭 합산 후의 score"가 된다. 이 차이가 순위를 바꾼다. MV는 DB 원장에서 기간 전체를 직접 집계하여 **정확한 기간 랭킹**을 제공하기 위해 존재한다.

두 시스템이 다른 결과를 내는 것은 버그가 아니라 설계 의도다. 그렇다면 일간/주간/월간 집계는 정확히 어떻게, 왜 달라지는가? 이 글에서 다루는 것은 그 차이를 만들어내고, 보장하고, 안정적으로 생산하기 위한 설계 판단들이다.

---

## 2. 집계가 달라지는 구조

### 2.1 차이를 만드는 판단 — "주간 베스트"는 총 판매량인가, 최근 인기인가

일간/주간/월간 집계가 달라지려면 Score 계산 방식이 그 차이를 허용해야 한다. MV의 Score를 어떤 방식으로 계산할 것인가 — 이 질문이 집계 차이의 출발점이다.

#### 왜 이 판단이 필요했는가

Redis monthly가 이미 지수 감쇠(`daily × 0.97^i`)를 사용하고 있었다. MV도 같은 방식을 쓸 수 있다. 그런데 **MV가 Redis와 같은 결과를 내면, MV를 만들 이유가 없다.**

#### 검토한 방식

| 방식 | 계산 | 특성 |
|------|------|------|
| **균등 합산** | `score = f(SUM(30일 메트릭))` | 30일 전이나 오늘이나 동등한 가중치. "기간 총 실적" |
| **지수 감쇠** | `monthly = Σ(daily × 0.97^i)` | 최근 데이터에 높은 가중치. 반감기 약 23일 |
| **일평균** | `score = f(SUM / 전시일수)` | 전시 기간에 관계없이 "일당 성과" |

#### 결정: 균등 합산

**"이번 달 베스트셀러 = 총 판매량 기준"이 이커머스 공개 랭킹 보드의 업계 표준이다.** 소비자가 "인기 상품 TOP 100"을 볼 때 기대하는 것은 "가장 많이 팔린 상품"이지, "일평균 판매량이 높은 상품"이 아니다.

두 시스템의 역할 분담은 이렇게 된다:

| | Redis (Speed Layer) | MV (Batch Layer) |
|------|---------------------|-------------------|
| **비즈니스 의미** | "지금 뜨는 상품" (트렌드) | "이번 달 베스트셀러" (누적 성과) |
| **Score 방식** | 지수 감쇠 | 균등 합산 |
| **소비자 시나리오** | 메인 페이지 실시간 인기 | 카테고리별 베스트, 기간별 랭킹 |

균등 합산은 전시 기간이 긴 상품이 유리하다는 트레이드오프가 있다. 지수 감쇠로 이를 희석할 수 있지만, 그러면 Redis와 결과가 수렴하여 MV의 존재 가치가 떨어진다.

#### 테스트가 보여준 것

10만 상품 × 30일(300만 행) 테스트에서, 동일한 데이터에 균등 합산을 적용한 결과:

- **weekly 1위**: product_5000 (급상승 — 최근 7일 폭발)
- **monthly 1위**: product_15000 (장기강자 — 30일 꾸준히 높음)

같은 Score 공식인데 시간 윈도우만 달라도 1위가 완전히 다르다. 1,020개 상품으로 실제 API까지 검증한 결과도 동일했다:

| 순위 | 일간 (Redis) | 주간 (MV) | 월간 (MV) |
|:----:|-------------|-----------|-----------|
| 1 | 아디다스 캠퍼스 올리브 **(바이럴)** | 나이키 에어리프트 카키 **(급상승)** | 반스 슬립온 올리브 **(장기강자)** |
| 2 | 살로몬 아웃펄스 네이비 **(바이럴)** | 컨버스 런스타하이크 그레이 **(급상승)** | 스투시 카고바지 화이트 **(장기강자)** |
| 3 | 뉴발란스 530 올리브 **(바이럴)** | 스투시 월드투어후디 카키 **(급상승)** | 리복 클럽C85 인디고 **(장기강자)** |

반대 방향도 있다. 20개 상품 테스트에서 하락추세 상품(메종키츠네)이 **월간 1위인데 일간/주간 19위**였다 — 과거 23일의 실적이 월간에는 남지만 최근 급락은 즉시 반영된다.

**어떤 시간 윈도우를 선택하느냐가 "인기 상품"의 정의 자체를 바꾼다.** 하나의 랭킹만 제공하면 어떤 관점은 반드시 누락된다. 일간만 보여주면 장기 스테디셀러가 사라지고, 월간만 보여주면 바이럴 상품이 보이지 않는다.

---

### 2.2 차이가 정확하려면 — 매번 원장에서 재계산하는 게 비효율 아닌가

시간 윈도우별로 다른 랭킹을 보여주는 것은 2.1에서 가능해졌다. 그런데 그 차이가 **정확한** 차이인가? MV는 매일 원장(product_metrics)에서 7일/30일 전체를 GROUP BY로 새로 집계한다. 증분 계산(어제 결과 - 가장 오래된 날 + 오늘)이 데이터 처리량을 93%(월간 기준) 줄일 수 있다.

#### 왜 이 판단이 필요했는가

월간 기준 30일분을 매일 재계산하는 것은 29/30 = 97%의 데이터를 중복 처리하는 것처럼 보인다. "약간 정도는 틀어져도 사용자가 모를 텐데, 효율성과 장애 대응 관점에서 증분이 낫지 않을까?"라는 질문이 나왔다.

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

성능 차이(Partitioning 4 Worker 기준 ~10초 vs ~3초)는 **1일 1회 배치에서 운영 영향이 없다.** 증분이 유리해지는 전환점은 배치 주기가 5분 이하로 빈번해질 때다.

#### 테스트가 보여준 것

E2E 테스트 시나리오 #7(취소 반영 테스트)에서 이 판단을 검증했다:

```
상품 A: 매출 100만 / 취소 0     → 순매출 100만
상품 B: 매출 200만 / 취소 150만  → 순매출 50만

결과: 상품 A가 1위 (총매출이 아닌 순매출 기준)
```

전체 재계산 덕분에, 취소가 나중에 발생해도 다음 배치에서 자동으로 반영된다. 증분이었다면 원주문 날짜의 취소 변경을 놓쳤을 것이다.

MV의 존재 이유가 "Redis 근사치와 다른 정확한 기간 집계"인데, 과거 데이터 변경을 반영하지 못하는 증분 방식을 쓰면 MV의 정확성이 약해진다.

---

### 2.3 차이를 안정적으로 생산하려면 — Chunk vs Tasklet: 90개 실무 Job이 알려준 것

Score 방식과 전체 재계산으로 정확한 기간별 랭킹 차이를 만들 수 있게 되었다. 이제 이것을 매일 안정적으로 생산하는 처리 모델을 선택해야 한다.

#### 왜 이 판단이 필요했는가

이 작업은 Tasklet으로도 가능하다. `INSERT INTO...SELECT + RANK() OVER + LIMIT 100`으로 SQL 한 문장이면 끝이고, 네트워크 왕복도 0이다.

실무 배치 앱 2개(총 90개 Job)를 분석했더니 통계/집계 Job의 대다수(10개 중 10개)가 Tasklet이었다. 처음에는 "Tasklet이 보편적"이라고 결론 내렸는데, 다시 생각해보니 이것은 **한 조직의 패턴을 업계 표준으로 확대 해석**한 것이었다.

두 앱 모두 MyBatis + SQL 중심 아키텍처여서 Tasklet(`INSERT INTO...SELECT`)이 자연스러운 선택이었다. Spring Batch 프레임워크 자체는 Chunk를 중심으로 설계되어 있고, retry/skip/restart 등 운영 기능이 Chunk에만 제공된다.

#### Tasklet이 맞는 조건

90개 Job 분석에서 도출한 Tasklet 조건은 세 가지다:

| 조건 | 설명 |
|------|------|
| SQL 한 문장으로 완결 | Java 변환이 전혀 없고 DB → DB 이동 |
| retry/skip이 불필요 | 실패 시 전체 재실행해도 수초 내 완료 |
| 중간 상태가 없음 | 처리 중 실패해도 "부분 완료" 상태가 의미 없음 |

우리의 MV TOP 100 적재는 세 조건을 모두 충족한다. 그런데도 Chunk를 선택한 이유가 있다.

#### 90개 Job이 빠뜨리고 있는 것

```
분석한 90개 Job의 운영 기능 사용 현황:

  faultTolerant()          → 0개
  retry() / retryLimit     → 0개
  skip() / skipLimit       → 0개
  ItemReadListener         → 0개
  ChunkListener            → 0개
  allowStartIfComplete     → 0개
```

**90개 Job 중 단 하나도 retry, skip, restart를 사용하지 않는다.** 이것은 "안 써도 된다"가 아니라, **"1건의 일시적 DB 에러가 전체 배치를 실패시키는 구조로 운영하고 있다"**는 뜻이다. 야간 배치가 데드락으로 실패하면 아침에 출근해서 수동 재실행해야 한다. `faultTolerant().retry(3)`를 걸어두면 자동으로 복구됐을 에러다.

#### 결정: Chunk

Chunk를 선택하면 Spring Batch의 운영 기능을 활용할 수 있다:

- **`faultTolerant + retry + ExponentialBackOffPolicy`**: 일시적 DB 에러(데드락, 커넥션 타임아웃) 시 100ms → 200ms → 400ms 간격으로 자동 재시도
- **`StepExecution` 자동 기록**: 각 Worker별 readCount, writeCount를 Spring Batch가 추적
- **`StepMonitorListener`**: 실패 시 알림

100건에 대한 네트워크 왕복 비용(< 1ms)보다 이 운영 기능의 가치가 크다고 판단했다. 남들이 안 쓰니까 안 써도 되는 것이 아니라, 프레임워크가 제공하는 운영 기능을 활용하여 야간 배치의 자동 복구 가능성을 높이는 것이 설계 의도다.

---

## 3. 차이를 빠르게 만들려면 — 구현: 3-Step Chunk Job

Score 방식(2.1)이 차이를 만들고, 전체 재계산(2.2)이 정확성을 보장하고, Chunk(2.3)가 안정성을 제공한다. 남은 문제는 **속도**다. 10만 상품 × 300만 행을 매일 전체 재계산하면서도, 배치가 운영 부담이 되지 않으려면 처리 구조가 뒷받침되어야 한다.

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

Reader로 `JdbcCursorItemReader`를 선택했다. 이유는 GROUP BY 집계 쿼리에서 `JdbcPagingItemReader`가 치명적이기 때문이다.

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

그런데 CursorReader는 하나의 ResultSet을 열어두고 `next()`로 이동하는 구조여서 **멀티스레드에서 사용할 수 없다.** 두 스레드가 동시에 `next()`를 호출하면 커서가 밀려 데이터가 누락된다.

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

이 범위 분할 로직은 Spring Batch 공식 샘플의 [`ColumnRangePartitioner`](https://github.com/SpringOne2GX-2014/spring-batch-performance-tuning)와 동일한 패턴이다:

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

우리의 `createPartitioner`도 `SELECT MIN(product_id), MAX(product_id)`로 범위를 구하고 gridSize로 나누어 `ExecutionContext`에 담는다. 공식 샘플이 단일 테이블의 PK 범위 분할을 기본 패턴으로 제시하고 있고, 우리는 여기에 `metric_date` 필터를 추가한 것이다.

각 Worker가 자기 범위의 GROUP BY만 실행하므로 ResultSet 공유 문제가 없다. 결과는 staging 테이블에 모이고, mergeStep에서 Global TOP 100을 추출한다.

### 벤치마크: gridSize=1 vs gridSize=4

10만 상품 × 300만 행에서 `ReflectionTestUtils`로 gridSize만 바꿔서 같은 데이터를 2회 실행한 결과:

| 구성 | weekly 소요 시간 | Worker당 상품 수 |
|------|----------------|--------------|
| gridSize=1 (단일 스레드) | **3,740ms** | 100,000 |
| gridSize=4 (4 Partition 병렬) | **1,763ms** | 25,000 |
| **향상률** | **2.1x** | |

이론적 상한은 4x지만, 실측은 2.1x다. 차이의 원인은 Amdahl's Law — Partitioner의 `SELECT DISTINCT product_id` 쿼리, mergeStep의 `ROW_NUMBER() OVER`, JobRepository 메타데이터 저장 등 직렬 구간이 전체의 일부를 차지한다. 또한 Testcontainers MySQL(`innodb-buffer-pool-size=256M`)의 제약과 4개 Worker의 IO 경합도 영향을 준다.

그래도 2.1x는 의미 있다. 1일 1회 배치에서 3.7초와 1.8초의 절대적 차이는 크지 않지만, 데이터가 10배(100만 상품)로 늘어나면 37초 vs 18초로 벌어진다. 병렬화의 효과는 규모에 비례한다.

다른 사례에서도 유사한 패턴이 관찰된다. [prostars.net의 Partitioner 성능 측정](https://prostars.net/357)에서:

| Partition | 소요 시간 | 향상률 |
|-----------|----------|--------|
| 1 | 30s 809ms | — |
| 5 | 17s 319ms | **1.8x** |
| 10 | 17s 529ms | 1.8x |
| 15 | 17s 529ms | 1.8x |

> "파티션을 크게 설정한다고 무조건 성능이 좋아지는 것은 아니다"

partition=5 이후 향상률이 정체되는 것은 우리의 2.1x(gridSize=4)와 일맥상통한다. Amdahl's Law에 의해 직렬 구간이 병목이 되면 Worker를 아무리 늘려도 한계가 있다. 또한 thread pool size=1로 제한하면 partition=5에서도 **2분 15초**로 급격히 느려지는데, Partitioning은 스레드 풀과 함께 써야 의미가 있다는 것을 보여준다.

---

## 4. 시행착오

### `@SpringBatchTest`가 private 메서드를 몰래 실행한다

```
No matching arguments found for method: runJob
```

`@SpringBatchTest`의 `JobScopeTestExecutionListener`는 테스트 클래스의 **모든 메서드**를 `getDeclaredMethods()`로 스캔한다. `JobExecution`을 반환하는 메서드를 찾으면 인자 없이 호출을 시도한다.

테스트 헬퍼 메서드 `private JobExecution runJob(String scope)`가 탐지 대상이 되어 실패했다. 반환 타입을 `BatchStatus`로 변경하면 스캔 대상에서 제외된다. 공식 문서에는 이 동작이 기술되어 있지 않다.

---

## 5. 실전에서라면

### gridSize 동적 조정

현재 gridSize를 4로 고정했지만, 실무에서는 커넥션 풀 크기와 CPU 코어 수에 연동해야 한다. 배치 전용 DataSource의 커넥션 풀이 10이면 gridSize를 8 이상으로 잡으면 커넥션 고갈이 발생한다. `@Value`로 외부화했으므로 프로파일별 설정으로 대응 가능하다.

### 스테이징 테이블의 비용

상품 100만 개면 스테이징에 100만 행이 적재된다. mergeStep에서 TOP 100만 추출하고 나머지는 cleanup에서 삭제하지만, 이 중간 저장 비용이 Partitioning의 병렬 처리 이점을 상쇄할 수 있다. 처리 속도뿐 아니라 디스크 I/O, 트랜잭션 로그 크기도 고려해야 한다.

### Redis weekly/monthly 제거

MV 도입 후 Redis의 weekly/monthly carry-over는 내부 모니터링용으로만 유지하거나 제거해야 한다. 두 경로가 공존하면 "어느 쪽이 정답인가"라는 혼란이 생긴다. scope별 단일 소스 원칙(daily → Redis, weekly/monthly → MV)을 유지하는 것이 데이터 일관성의 핵심이다.

### Score 공식의 중앙화

Score 공식(`LOG10 + 가중치`)은 원래 Streamer, Batch Correction, MV Job SQL, API Drift Scheduler 4곳에 분산되어 있었다. MV Job을 추가하면서 5번째 복사본이 생기는 시점에서 `ScoreFormula`(modules/jpa)로 중앙화했다. 가중치도 `ScoreFormula.Weights` record로 타입을 통일하고 `application.yml`에서 주입한다. 공식이 한 곳에만 존재하므로 변경 시 누락이 구조적으로 불가능해졌다.

---

## 6. 돌아보며

10주 전에는 "Redis에 ZADD하면 랭킹이 나온다"고 생각했다. 틀린 말은 아니지만, 그것이 전부가 아니었다.

이커머스 랭킹은 "어떤 시간 윈도우로 보느냐"에 따라 완전히 다른 결과를 낸다. 오늘 SNS에서 터진 상품, 이번 주 꾸준히 팔린 상품, 한 달간 스테디셀러인 상품은 모두 "인기 상품"이지만, 하나의 랭킹으로는 세 관점을 동시에 담을 수 없다.

Lambda Architecture(실시간 Redis + 배치 MV)를 선택한 이유도 여기에 있다. **두 Layer의 가치는 같은 결과를 내는 것이 아니라, 같은 데이터로 다른 관점을 제공하는 것이다.** 실시간 경로는 "지금 뜨는 상품"을, 배치 경로는 "기간 동안 검증된 상품"을 각각 담당한다. 두 경로가 서로 다른 것은 설계 의도이며, 테스트는 그 설계 의도가 실제로 동작하는지를 확인하는 과정이었다.

이 글에서 다룬 "파티셔닝 → 병렬 집계 → merge"라는 Map-Reduce 패턴은 규모가 다른 시스템에서도 반복적으로 등장한다:

- [**Netflix Distributed Counter**](https://netflixtechblog.com/netflixs-distributed-counter-abstraction-8d0c45eb66b2): 시간 기반 파티셔닝 + Rollup 병렬 집계 → merge. *"A background rollup process continuously aggregates these events using time-based windows, storing intermediate counts in a persistent store."* 75K RPS, single-digit ms 레이턴시를 이 구조로 달성한다. 우리의 3-Step(Cleanup → Partitioned Aggregate → Merge)과 구조적으로 동일하다.

- [**Shopify BFCM Live Map**](https://shopify.engineering/bfcm-live-map-2021-apache-flink-redesign): 텀블링 윈도우 5분 간격 TOP 500 집계. *"Redis would quickly become a bottleneck due to the increase in the number of published messages and subscribers."* BFCM 피크 **초당 100만 체크아웃 이벤트**를 처리하면서 Redis 병목을 Flink로 해소했다. "실시간 경로의 한계를 배치/스트림 집계로 보완한다"는 점에서 우리의 Lambda Architecture 선택과 같은 맥락이다.

설계에서 가장 어려웠던 것은 "정답을 찾는 것"이 아니라 **"선택하지 않은 대안을 납득할 수 있게 정리하는 것"**이었다. 균등 합산을 선택하면서 지수 감쇠의 장점을 인정하고, 전체 재계산을 선택하면서 증분의 효율성을 인정하고, Chunk를 선택하면서 Tasklet의 단순함을 인정하는 과정이 설계 판단이었다. 어느 쪽이 "더 좋다"가 아니라 **"우리 상황에서 왜 이쪽인가"**를 설명할 수 있는 것이 중요했다.
