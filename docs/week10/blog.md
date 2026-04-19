# N건의 데이터를 1회로 - Spring Batch로 주간/월간 랭킹 집계하기

> TL;DR: 주간·월간 랭킹을 실시간으로 집계하면 DB 부하가 선형으로 증가한다. 배치로 하루 1회만 계산하고 집계 테이블에 저장하면, 조회는 단순 SELECT 한 줄로 끝난다.

---

## 들어가며

Redis ZSET으로 실시간 일간 랭킹을 구현한 적이 있다. 주문이 발생할 때마다 메시지가 흘러들어오고, 그게 실시간으로 점수에 반영되는 방식이었다. 실시간으로 집계 할 수 있다는 점이 , "이걸 그냥 주간/월간에도 쓰면 안 되나?" 라는 생각이 자연스럽게 들었다.

근데 잠깐 생각해보면 바로 문제가 보인다.

주간 랭킹을 실시간으로 제공하려면 어떻게 해야 할까? 요청이 들어올 때마다 7일치 데이터를 전부 긁어서 GROUP BY하고 정렬해야 한다. 상품이 1,000개고 하루는 24시간 이니, 최악의 경우 주간 조회 한 번에 7 × 24 × 1,000 = 168,000개 row를 Full Scan + 집계 + 정렬해야 한다(실제로는 모든 상품이 모든 시간에 이벤트가 발생하진 않으므로 이보다 적다). 트래픽이 몰리는 순간에는 이 쿼리가 동시에 수백 번 실행될 수 있다.

이 문제를 해결하기 위해 주간/월간 랭킹 집계에 **Spring Batch**와 **Materialized View** 패턴을 도입했다.

---

## Batch란

Batch라는 단어는 원래 "한 묶음"이라는 뜻이다. 빵집에서 한 번에 굽는 빵 한 판, 공장에서 한 번에 처리하는 제품 묶음. 여기서 나온 개념이 **배치 처리(Batch Processing)**다.

웹 요청-응답 방식이 "손님이 주문할 때마다 즉시 요리해주는 레스토랑"이라면, 배치 처리는 "새벽 4시에 오늘 재고를 한꺼번에 정리하는 마트 직원"에 더 가깝다. 아무도 없는 조용한 시간에, 대량의 데이터를 몰아서 처리한다.

### 실무에서 배치가 필요한 순간들

- **월간 정산**: PG사 매출 데이터를 모아서 매달 1일 새벽에 정산 테이블 생성
- **랭킹/통계**: 일간·주간·월간 인기 상품 집계
- **데이터 정리**: 만료된 쿠폰 삭제, 오래된 로그 제거
- **DW 적재**: 서비스 DB에서 BigQuery, Redshift 등 분석용 창고로 데이터 옮기기

공통점이 있다. 요청자가 없어도 실행되고, 대량의 데이터를 다루며, **정확성과 효율성이 신속성보다 중요**하다.

---

## Spring Batch 핵심 구조

Spring Batch의 구조를 처음 보면 용어가 많아 막막하다. 핵심만 먼저 잡아보자.

```
Job
 └─ Step
      └─ Chunk (대용량 처리)
           ├─ ItemReader   (읽기)
           ├─ ItemProcessor (가공, 선택)
           └─ ItemWriter   (저장)
```

### Job

배치 실행의 최상위 단위다. "주간 랭킹 집계"라는 작업 전체를 묶는 설계도라고 보면 된다. 하나의 Job은 여러 Step으로 구성되고, `JobBuilder`로 만든다.

```java
@Bean
public Job weeklyRankingJob() {
    return new JobBuilder("weeklyRankingJob", jobRepository)
            .incrementer(new RunIdIncrementer()) // 매 실행마다 고유 ID 부여
            .start(weeklyRankingStep())
            .listener(jobListener)
            .build();
}
```

`RunIdIncrementer`를 붙이는 이유가 있다. Spring Batch는 동일한 `JobParameters`로 이미 완료된 Job은 재실행을 막는다. `RunIdIncrementer`는 실행마다 `run.id`를 자동으로 증가시켜서, 같은 날짜 파라미터로도 매번 새 실행으로 인식되게 해준다.

### Step

Job을 구성하는 세부 단계다. 하나의 Job 안에 여러 Step이 있을 수 있고, Step들은 순서대로 실행된다. 각 Step은 독립적으로 성공/실패 상태를 갖기 때문에, 특정 Step이 실패하면 그 Step부터 재시작할 수 있다.

### JobParameters

Job 실행 시 외부에서 주입하는 값이다. 이번 구현에서는 `targetDate`를 파라미터로 받아 어떤 기간의 데이터를 집계할지 결정한다.

```java
new JobParametersBuilder()
    .addLocalDate("targetDate", LocalDate.of(2026, 4, 16))
    .toJobParameters();
```

Step 내부에서는 `@StepScope` + `@Value("#{jobParameters['targetDate']}")`로 꺼내 쓴다. Step이 실제로 실행되는 시점에 값이 바인딩되는 지연 초기화 방식이다.

### JobRepository

Spring Batch가 Job 실행 이력을 DB에 자동으로 저장하는 저장소다. 개발자가 직접 건드리지 않아도 프레임워크가 알아서 `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` 등의 테이블에 기록한다. 덕분에 언제 실행됐고, 몇 건 처리됐고, 실패했다면 어디서 멈췄는지 추적할 수 있다.

### Chunk

Step 안에서 데이터를 N건씩 나눠 읽고-가공하고-저장하는 방식.

### Chunk가 왜 필요할까?

10만 건의 데이터를 처리한다고 가정해보자. 전부 메모리에 올려서 한 번에 처리하면 OOM(Out of Memory)이 날 수 있다. Chunk는 이걸 1,000건씩 나눠서 처리한다. 1,000건 읽고 → 처리하고 → 저장하고 → 다음 1,000건. 트랜잭션도 이 단위로 묶인다.

실패 시 롤백 범위는 어디서 예외가 났느냐에 따라 다르다. Reader나 Processor에서 예외가 나면 해당 건만 문제가 되지만, **Writer에서 예외가 나면 해당 청크 전체가 롤백**된다. 이미 읽고 가공한 데이터도 저장을 못 한 채 롤백되는 것이다. 이 때문에 Spring Batch는 Skip과 Retry 정책을 제공한다. 특정 예외는 건너뛰거나(`skip`), 몇 번까지 재시도할지(`retry`) 설정해두면 일부 건이 실패해도 배치 전체가 중단되지 않고 계속 진행할 수 있다. 이번 구현은 100건 전체를 단일 Chunk로 처리하는 구조라 Writer 실패 시 전체 롤백 후 재실행하는 방식을 택했다.

### Tasklet은 언제 써야할까?

Chunk와 별개로 **Tasklet**이라는 방식도 있다. 단순히 "이 SQL 한 번 실행해"거나 "이 파일 삭제해" 같은 1회성 작업에 적합하다. Reader/Processor/Writer를 굳이 만들 필요 없이 `execute()` 메서드 하나에 로직을 담는다.

```java
@Bean
public Step cleanupStep(JobRepository jobRepository, PlatformTransactionManager txManager) {
    return new StepBuilder("cleanupStep", jobRepository)
        .tasklet((contribution, chunkContext) -> {
            expiredCouponRepository.deleteExpired();
            return RepeatStatus.FINISHED;
        }, txManager)
        .build();
}
```

---

## 실시간 집계 vs 배치 + MV

잠깐 짚고 넘어가면, 일간 랭킹도 API 요청 시점에 집계하는 게 아니다. Kafka 이벤트가 들어올 때마다 streamer가 Redis ZSET을 갱신하고, API는 그걸 단순히 읽어올 뿐이다. "실시간"이라는 표현은 API가 집계한다는 뜻이 아니라, ZSET이 이벤트 단위로 갱신된다는 뜻이다. 아래에서 말하는 "실시간 집계 방식"은 주간/월간 랭킹을 만약 **요청마다 DB에서 직접 계산한다면** 어떻게 될지에 대한 가상 시나리오다.

참고로 여기서 말하는 MV(Materialized View)는 PostgreSQL이나 Oracle처럼 DB가 직접 제공하는 기능이 아니다. MySQL은 Materialized View를 내장하고 있지 않아서, 이번 구현처럼 **집계 결과를 별도 테이블에 미리 저장하는 방식**을 사용했다. 정확히는 Materialized View 패턴을 흉내 낸 **Summary Table(집계 테이블)**에 가깝다. "MV 테이블"이라고 부르는 건 이 패턴에서 온 표현이다.

### 실시간 집계 방식

```sql
-- 랭킹 조회 요청마다 실행
SELECT product_id,
       LN(1 + SUM(view_count))    * ? +
       LN(1 + SUM(like_count))    * ? +
       LN(1 + SUM(order_amount))  * ? AS score
FROM product_metrics_hourly
WHERE bucket_hour BETWEEN :start AND :end
GROUP BY product_id
ORDER BY score DESC
LIMIT 100;
```

- 매 요청마다 전체 기간 데이터를 Full Scan + GROUP BY + Sort
- 주간 = 7일 × 24시간 = 168개 row/상품, 월간 = 720개 row/상품
- **요청이 많아질수록 DB 부하가 선형으로 증가**

### 배치 + MV 방식

```sql
-- 배치가 새벽에 1회 실행해 결과를 MV 테이블에 저장
-- 랭킹 조회 요청은 단순 PK 조회만
SELECT * FROM mv_product_rank_weekly WHERE base_date = ?;
```

- 집계는 배치가 새벽에 **단 1회**만 수행
- 조회 시점에는 이미 계산된 결과를 단순 SELECT → **요청 수와 무관하게 응답 시간 일정**

| 항목 | 실시간 집계 | 배치 + MV |
|------|-----------|----------|
| 조회 쿼리 비용 | Full Scan + GROUP BY + Sort | PK 단순 조회 |
| 동시 요청 증가 시 | DB 부하 선형 증가 | 부하 없음 |
| 응답 시간 | 데이터 양에 비례 | 항상 일정 |
| 집계 실행 횟수 | 요청마다 | 하루 1회 |
| 데이터 신선도 | 실시간 | 배치 실행 주기만큼 지연 |

물론 단점도 있다. 배치가 새벽 2시에 실행된다면 오전 10시에 조회해도 어제 기준 랭킹을 보게 된다. 하지만 주간/월간 랭킹은 하루 단위로 변해도 사용자가 체감하는 차이가 크지 않다. **일간 랭킹은 Redis ZSET으로 실시간, 주간·월간은 배치 + MV로** 역할을 나누는 게 자연스럽다.

```
일간 랭킹  → Redis ZSET  (실시간, 메모리 기반)
주간 랭킹  → MV 테이블   (배치 집계, 하루 1회 갱신)
월간 랭킹  → MV 테이블   (배치 집계, 하루 1회 갱신)
```

---

## 주간/월간 랭킹 집계를 구현하며 고민했던 선택들

### 선택 1. Chunk vs Tasklet — 어떤 방식으로 집계할까

Tasklet 안에서 직접 페이징 처리를 구현하는 방식도 충분히 가능하다. 사실 구현 초반에 그쪽이 더 자유롭고 직관적이라고 생각했다.

그런데 이번 집계 흐름을 다시 보면:

```
product_metrics_hourly에서 기간 범위 읽기
→ product_id 별 집계 (SUM)
→ 점수 계산 후 TOP 100 정렬
→ mv_product_rank_weekly 저장
```

Spring Batch Chunk의 기본 전제는 "1건 읽기 → 1건 가공 → N건 쓰기"다. 집계(GROUP BY)가 포함되면 여러 시간 버킷 row → 1개 product_id 집계값이 돼서 구조가 어색해진다.

그렇다면, **SQL 단에서 GROUP BY 집계까지 처리해버리면 어떨까?**

```sql
SELECT product_id,
       LN(1 + SUM(view_count))    * ? +
       LN(1 + SUM(like_count))    * ? +
       LN(1 + SUM(order_amount))  * ? AS score
FROM product_metrics_hourly
WHERE bucket_hour >= ? AND bucket_hour < ?
GROUP BY product_id
ORDER BY score DESC
LIMIT 100
```

Reader가 이미 집계된 결과(product_id 1건 = 1 row)를 읽으므로 Chunk 구조에 딱 맞아떨어진다. Spring Batch가 제공하는 페이징, 재시작, 건수 추적 혜택도 그대로 받을 수 있다.

**결정: SQL 집계 + Spring Batch Chunk 방식**

> 이 방식은 DB가 GROUP BY 집계를 담당하므로 상품 수가 수천~수만 개 수준일 때 효율적이다. 단, 상품이 10만 개를 넘어가고 `bucket_hour`와 `product_id` 조합에 인덱스가 없다면 GROUP BY 자체가 풀스캔이 되어 배치가 느려질 수 있다. 대규모 서비스라면 `(bucket_hour, product_id)` 복합 인덱스를 확인하거나, 일간 집계 테이블을 중간에 두는 방식을 고려해야 한다.

### 선택 2. ItemReader 방식 — JdbcCursor vs JdbcPaging

JDBC 기반 Reader는 크게 두 가지다.

- **JdbcCursorItemReader**: DB 커넥션을 유지하면서 한 줄씩 스트리밍
- **JdbcPagingItemReader**: 청크 처리마다 새 쿼리로 페이지를 가져오고 커넥션 반납

JdbcPagingItemReader는 실패 시 마지막 페이지부터 재시작이 가능하고 커넥션 점유 시간이 짧다는 장점이 있다. 하지만 이번 구현의 특성을 따져보면:

- 읽는 데이터는 product_id별 집계 결과로 **최대 100건** (상품 수만큼)
- 1일 1회 실행, 실행 시간이 짧아 **재시작 필요성이 낮음**
- GROUP BY 쿼리에 페이징 키를 추가하면 **정확성 문제 발생**

마지막 항목이 핵심이다. `JdbcPagingItemReader`는 내부적으로 정렬 키(`sortKeys`)를 기반으로 `WHERE sort_key > :lastValue` 조건을 자동으로 붙여서 페이지를 넘긴다. 이 때 정렬 키는 **유니크**해야 한다. 그런데 이번 집계 결과의 정렬 기준인 `score`는 여러 상품이 동일한 값을 가질 수 있다. score가 중복되면 페이지 경계에서 같은 row가 두 번 읽히거나 아예 누락되는 문제가 생긴다. 단순히 쿼리가 복잡해진다는 수준이 아니라, 랭킹 결과 자체가 틀려질 수 있는 **정확성 문제**다.

**결정: JdbcCursorItemReader**

> 이 선택은 결과가 최대 100건(`LIMIT 100`)이라는 전제 위에 있다. Cursor 방식은 DB 커넥션을 Step 전체 실행 동안 점유하기 때문에, 수만 건 이상을 스트리밍하면 배치 실행 시간이 길어지면서 네트워크 타임아웃이나 커넥션 풀 고갈 위험이 생긴다. 만약 TOP 100이 아니라 전체 상품 랭킹을 뽑아야 하는 요구사항으로 바뀐다면 `JdbcPagingItemReader`로 교체를 검토해야 한다.

```java
@StepScope
@Bean("weeklyRankingReader")
public JdbcCursorItemReader<ProductMetricsAggregate> weeklyRankingReader(
        DataSource dataSource,
        RankingWeights rankingWeights,
        @Value("#{jobParameters['targetDate']}") LocalDate targetDate
) {
    LocalDateTime startTime = targetDate.minusDays(7).atStartOfDay();
    LocalDateTime endTime = targetDate.atStartOfDay();

    return new JdbcCursorItemReaderBuilder<ProductMetricsAggregate>()
            .name("weeklyRankingReader")
            .dataSource(dataSource)
            .sql(AGGREGATION_SQL)
            .preparedStatementSetter(ps -> {
                ps.setDouble(1, rankingWeights.view());
                ps.setDouble(2, rankingWeights.like());
                ps.setDouble(3, rankingWeights.order());
                ps.setObject(4, startTime);
                ps.setObject(5, endTime);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregate(
                    rs.getLong("product_id"),
                    rs.getDouble("score")
            ))
            .build();
}
```

### 선택 3. 슬라이딩 윈도우 vs ISO 주차

"주간 베스트"를 어떻게 정의하느냐의 문제다.

| 기준 | 설명 | 예시 (기준일: 2024-01-10) |
|------|------|--------------------------|
| 슬라이딩 윈도우 | 기준일 직전 7일 | 01-04 ~ 01-10 |
| ISO 주차 | 해당 주 월~일 | 01-08 ~ 01-14 |

ISO 주차 방식은 과거 스냅샷 조회나 BI 리포트에 강점이 있다. 하지만 이번 목적은 **사용자에게 지금 시점 기준의 인기 상품을 보여주는 것**이다. 슬라이딩 윈도우는 어느 요일에 접속해도 항상 최근 7일 기준의 생생한 랭킹을 보여준다.

추가로, 배치는 새벽에 실행되므로 오늘 데이터는 당일 0시~배치 실행 시각까지만 쌓인 불완전한 상태다. 그래서 **어제(targetDate - 1일) 기준 직전 7일/30일**로 잡았다.

```
주간: targetDate - 7일  ~  targetDate - 1일
월간: targetDate - 30일 ~  targetDate - 1일
```

**결정: 슬라이딩 윈도우 (어제 기준 직전 N일)**

> **용어 정의**: 배치의 `targetDate`와 MV 테이블의 `base_date`, Facade의 `baseDate`는 서로 다른 값이다.
> - `targetDate`: 배치 실행 시 전달받는 파라미터. 보통 오늘 날짜.
> - `base_date` (MV 저장값): `targetDate - 1일`. 집계 범위의 마지막 날짜이자 API 조회 키.
> - Facade의 `baseDate`: API 호출 시 `date` 파라미터가 없으면 KST 기준 어제로 기본값 설정.
>
> 배치가 오늘(`targetDate`)을 받아 `base_date = 어제`로 저장하고, API는 `date` 생략 시 어제를 기준으로 조회하므로 두 값이 일치하게 된다.

### 선택 4. MV 갱신 전략 — UPSERT vs DELETE + INSERT

슬라이딩 윈도우 방식에서 TRUNCATE는 다른 날짜 데이터까지 지워버리므로 제외. UPSERT(INSERT ON DUPLICATE KEY UPDATE)와 DELETE + INSERT 중에서 골라야 했다.

UPSERT가 트랜잭션 범위가 작고 원자적 연산이라 처음엔 끌렸다. 그런데 이런 케이스를 생각해보면 문제가 생긴다.

1. 1차 실행 도중 실패 → 상품 A가 부분 적재된 상태
2. 2차 재실행 시 상품 A가 TOP 100 밖으로 밀림
3. 1차에서 들어간 상품 A 레코드가 그대로 잔류

결국 재실행 전에 해당 base_date 데이터를 먼저 DELETE해야 하는데, 그러면 DELETE + INSERT와 동일한 구조가 된다. UPSERT의 장점이 사라지는 것이다.

최대 100건이라 락 경합 문제도 없고, 코드가 단순하고 멱등성이 보장된다.

**결정: DELETE + INSERT (트랜잭션으로 묶어 원자적 처리)**

> **실무 팁**: 전체 테이블을 한 번에 교체하는 경우라면 `TRUNCATE`가 `DELETE`보다 훨씬 빠르다. 하지만 이번처럼 특정 `base_date`만 지워야 하는 상황에서는 `DELETE WHERE base_date = ?`가 맞다. 한편, 배치 실행 중 서비스 중단 없이 조회가 계속 가능해야 한다면 **Temp Table Swap** 전략을 고려할 수 있다. 새 집계 결과를 임시 테이블(`mv_product_rank_weekly_tmp`)에 먼저 채운 뒤, 테이블 이름을 원자적으로 교체(`RENAME TABLE`)하는 방식이다. 조회 요청이 DELETE 공백 시간에 빈 결과를 받는 일이 없어진다. 지금 구현은 100건 소규모라 공백 시간이 사실상 0에 수렴하지만, 수십만 건 규모로 커지면 의미 있는 선택지가 된다.

```java
@Transactional
@Override
public void replaceWeeklyRanking(LocalDate baseDate, List<MvProductRankRow> rows) {
    jdbcTemplate.update(DELETE_WEEKLY, baseDate);
    insertBatch(INSERT_WEEKLY, baseDate, rows);
}
```

`@Transactional`을 Repository에 직접 붙인 이유도 있다. Spring Batch Chunk 트랜잭션 안에서 호출될 때는 `REQUIRED` 전파로 Chunk 트랜잭션에 포함되어 **Chunk 커밋 시점에 함께 커밋**된다. Repository 메서드가 끝나는 시점이 아니라 Chunk 단위로 커밋이 결정되므로, 이 경우 `@Transactional`은 사실상 Chunk 트랜잭션에 합류할 뿐 독립적으로 커밋하지 않는다. 반면 배치 외부에서 이 메서드를 직접 호출하면 Repository 스스로 트랜잭션을 열고 커밋한다. 즉, `@Transactional`을 붙인 주목적은 **배치 외부 호출 시에도 DELETE + INSERT의 원자성을 Repository가 직접 보장하기 위해서**다.


---

## Listener로 배치 모니터링하기

배치가 실행되면 Spring Batch는 `BATCH_JOB_EXECUTION`, `BATCH_STEP_EXECUTION` 등의 테이블에 실행 이력을 자동으로 기록한다. 하지만 그것만으로는 부족하다. 언제 실행됐는지, 얼마나 걸렸는지, 실패했다면 어디서 왜 실패했는지 즉시 확인하고 싶다.

Spring Batch는 Job/Step/Chunk 각 단계에 훅을 걸 수 있는 Listener를 제공한다.

### JobListener — 실행 시간 추적

```java
@BeforeJob
void beforeJob(JobExecution jobExecution) {
    log.info("Job '{}' 시작", jobExecution.getJobInstance().getJobName());
    jobExecution.getExecutionContext().putLong("startTime", System.currentTimeMillis());
}

@AfterJob
void afterJob(JobExecution jobExecution) {
    var startTime = jobExecution.getExecutionContext().getLong("startTime");
    var duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
    log.info("총 소요 시간: {}시간 {}분 {}초",
        duration.toHours(), duration.toMinutes() % 60, duration.getSeconds() % 60);
}
```

`ExecutionContext`를 활용해 시작 시각을 저장하고, Job 종료 시점에 꺼내서 총 소요 시간을 계산한다.

### StepMonitorListener — 실패 감지 및 알림 포인트

```java
@Override
public ExitStatus afterStep(StepExecution stepExecution) {
    if (!stepExecution.getFailureExceptions().isEmpty()) {
        // Slack 등 외부 알림 채널 연동 포인트
        // notificationService.sendAlert(...)
        return ExitStatus.FAILED;
    }
    return ExitStatus.COMPLETED;
}
```

Step 실패 시 `ExitStatus.FAILED`를 명시적으로 반환해 Job 상태에 반영한다. 알림 채널 연동 포인트이기도 하다.

### ChunkListener — 처리 건수 추적

```java
@AfterChunk
void afterChunk(ChunkContext chunkContext) {
    log.info("청크 종료: readCount: {}, writeCount: {}",
        chunkContext.getStepContext().getStepExecution().getReadCount(),
        chunkContext.getStepContext().getStepExecution().getWriteCount());
}
```

청크마다 readCount/writeCount를 로깅하면 배치가 중간에 멈췄을 때 어디까지 처리됐는지 바로 알 수 있다.

---

## Spring Batch 테스트는 어떻게 하나

배치를 구현하고 나서 "이걸 어떻게 테스트하지?"라는 질문이 생겼다. 웹 요청은 MockMvc로 가짜 HTTP를 날리면 되는데, 배치는 Job을 실제로 실행시켜야 의미 있는 검증이 된다.

Spring Batch는 `@SpringBatchTest` 어노테이션을 제공한다. 이걸 붙이면 `JobLauncherTestUtils`가 자동으로 주입되고, 실제 Job을 실행해서 `JobExecution`을 받아볼 수 있다.

```java
@SpringBootTest
@SpringBatchTest
@TestPropertySource(properties = "spring.batch.job.name=" + WeeklyRankingJobConfig.JOB_NAME)
class WeeklyRankingJobE2ETest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Test
    void populatesMvTableWithRanking() throws Exception {
        // given
        // product_metrics_hourly에 데이터 세팅
        jobLauncherTestUtils.setJob(weeklyRankingJob);
        var params = new JobParametersBuilder()
            .addLocalDate("targetDate", LocalDate.of(2026, 4, 16))
            .toJobParameters();

        // when
        var execution = jobLauncherTestUtils.launchJob(params);

        // then
        assertThat(execution.getExitStatus().getExitCode())
            .isEqualTo(ExitStatus.COMPLETED.getExitCode());
        // mv_product_rank_weekly 테이블 직접 조회해서 rank, score 검증
    }
}
```

Testcontainers로 실제 MySQL을 띄우기 때문에, 테스트가 곧 운영과 동일한 DB 환경에서 돌아간다. `@TestPropertySource`로 Job 이름을 지정하면 해당 Job 빈만 활성화되어 깔끔하게 격리된다.

### Batch E2E와 API E2E를 분리한 이유

처음에는 "배치 실행 → API 조회"를 하나의 테스트에서 다 검증하려고 했다. 근데 그렇게 하면 문제가 생긴다. 배치 스펙이 바뀌어서 테스트가 실패했는지, API 조회 로직이 잘못돼서 실패했는지 실패 원인이 뒤섞인다.

그래서 두 레이어를 완전히 분리했다.

- **Batch E2E**: `product_metrics_hourly`에 데이터를 넣고 → Job 실행 → `mv_product_rank_weekly` 테이블에 올바르게 적재됐는지 검증
- **API E2E**: `mv_product_rank_weekly`에 직접 `jdbcTemplate.update()`로 데이터를 심고 → HTTP 요청 → 응답 JSON 검증

API E2E는 배치가 아예 없어도 독립적으로 실행된다. 배치가 실패한 날에도 API 테스트는 그대로 돌아가고, 반대로 배치 로직이 바뀌어도 API 테스트는 영향받지 않는다. 실패 원인이 명확하게 드러나는 구조다.

### 인상 깊었던 테스트 케이스들

**`failsWithoutTargetDate`** — 배치 파라미터 없이 실행하면 어떻게 될까?

처음 생각은 "그냥 COMPLETED가 되는 거 아닐까?" 였다. 그런데 실제로는 `targetDate`가 null이면 `JdbcCursorItemReader`가 날짜 계산을 하다가 NPE를 던지고, Spring Batch가 이를 잡아 `ExitStatus=FAILED`로 전환한다. 조용히 COMPLETED 되는 대신 명확하게 실패한다는 걸 검증하는 테스트다. 배치가 파라미터 없이 실행되는 실수를 조기에 잡아주는 안전망이다.

향후 개선 방향으로는 `JobParametersValidator`를 구현하면 ItemReader까지 도달하기 전에 Job 레벨에서 더 명시적인 에러 메시지로 실패시킬 수 있다.

**`replacesExistingMvOnRerun`** — 같은 날짜로 배치를 두 번 실행하면?

DELETE + INSERT 전략에서 멱등성을 검증하는 핵심 케이스다. 1차 실행으로 MV 테이블에 데이터를 적재한 뒤, 동일 `targetDate`로 2차 실행했을 때 기존 데이터가 완전히 교체되는지를 확인한다. 재실행 시 이전 데이터가 섞이거나 잔류하지 않음을 직접 증명한다.

**`limitsToTop100`** — 상품이 101개 이상이어도 MV에는 100건만 들어가는가?

이 케이스가 배치 E2E에서 가장 오래 걸렸다(주간 253ms, 월간 277ms). 101개 상품 데이터를 INSERT하고 Job을 실행하기 때문에 데이터 양이 가장 많아서다. 역설적으로 이 테스트가 실제 운영에서 대량 데이터를 처리할 때의 성능 힌트를 준다. 인덱스가 없으면 이 규모에서도 속도 차이가 눈에 띄기 시작한다는 것을.

---

## 스케줄링은 코드 밖에서

배치를 구현하면서 "그래서 이걸 언제 어떻게 실행하나?"라는 질문이 나온다. `@Scheduled`로 내부에서 트리거하는 방법이 가장 쉽지만, 이건 함정이다.

- 분산 환경에서 **중복 실행 위험**이 있다
- 실패해도 **모니터링/알림 기능이 없다**
- 배치 앱을 재배포하면 스케줄도 같이 영향을 받는다

현업에서 가장 현실적인 방법은 **Jenkins + Shell Script** 조합이다.

```
Jenkins Cron Trigger (매일 새벽 2시)
        │
        ▼
./scripts/run-weekly-ranking.sh
        │
        ▼
java -jar commerce-batch.jar \
  --spring.profiles.active=prd \
  --job.name=weeklyRankingJob \
  targetDate=2026-04-16
        │
        ▼
Jenkins → exit code 로 성공/실패 판단 → Slack 알림 등 후처리
```

배치 앱은 Job이 완료되면 프로세스가 종료되도록 `web-application-type: none`으로 설정한다. 스케줄링 로직은 전혀 없고, 파라미터만 받아서 실행하는 단순한 형태로 유지하는 것이 핵심이다.

`@ConditionalOnProperty`를 활용해 `--job.name=weeklyRankingJob`을 넘기면 해당 Job 빈만 로드되도록 격리시켰다.

```java
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Configuration
public class WeeklyRankingJobConfig {
    public static final String JOB_NAME = "weeklyRankingJob";
    // ...
}
```

> `spring.batch.job.name`은 Spring Boot 3 + Spring Batch 5 기준의 프로퍼티 이름이다. Spring Boot 2 + Spring Batch 4에서는 `spring.batch.job.names`(복수형)를 사용했다. 이 프로젝트는 Spring Boot 3.4.4 기반이므로 단수형이 맞다.

---

## 마치며

Spring Batch를 처음 써보면서 솔직히 "이게 꼭 필요한가?" 라는 생각이 들었다. 실제로 우리 회사에도 배치성 작업이 있는데, Spring Batch 없이 `@Scheduled` + 비즈니스 로직 직접 호출로 돌아가고 있다. 외부 API 연동이 포함된 작업이라 Chunk 방식으로 Reader/Processor/Writer를 나누기가 어색하고, 굳이 그럴 필요도 없어 보이는 상황이었을 거다.

이번에 직접 써보고 나서 생각이 조금 바뀌었다. Spring Batch가 진짜 빛을 발하는 건 **운영 중에 실패했을 때**인 것 같다. `@Scheduled`로 돌리는 방식은 실패하면 로그 뒤지고, 어디까지 처리됐는지 직접 확인하고, 중복 실행 안 되게 수동으로 막아야 한다. 반면 Spring Batch는 `BATCH_JOB_EXECUTION` 테이블에 어디서 실패했는지 다 남아있다. Chunk 기반 Step에서는 이전 실행이 `FAILED` 상태일 때 같은 JobParameters로 재실행하면 마지막 커밋된 Chunk 다음부터 이어서 처리할 수 있다(단, Tasklet은 중간 지점 재시작이 안 되고 처음부터 다시 실행된다).

그러면 외부 API가 포함된 배치는 Spring Batch를 쓰면 안 되는 걸까? 꼭 그렇진 않다. `@Scheduled`로 트리거하되, 외부 API 호출 부분을 Tasklet으로 감싸면 Batch의 **실행 이력 관리** 혜택은 그대로 누릴 수 있다. 다만 Tasklet은 중간 재시작이 되지 않으므로, 재실행 시 처음부터 다시 돌아가도 문제없는 멱등한 작업에만 적합하다. 외부 API 페이지네이션처럼 대량 데이터를 순차적으로 받아 DB에 저장하는 구조라면 오히려 Chunk 기반 Reader(API 호출)/Writer(DB 저장)로 구성하는 게 재시작 안전성 측면에서 더 낫다.

결국 Spring Batch는 "대용량 데이터 처리 프레임워크"이기 이전에, **배치 작업을 안전하게 운영하기 위한 프레임워크**라는 느낌이 더 강했다. 우리 회사 배치가 지금 당장 문제가 없더라도, 언젠가 데이터가 쌓이고 실패 재시작이 필요해지는 순간이 오면, 스프링 배치를 도입 해야 할 것 같다.
