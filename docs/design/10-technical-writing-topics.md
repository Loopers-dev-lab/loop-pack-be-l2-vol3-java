# 10. 테크니컬 라이팅 소재 모음

> Round 10 과제를 수행하면서 발생한 설계 고민, 트레이드오프, 판단 근거를 기록한다.
> 블로그 글의 소재로 활용한다.

---

## 소재 1: MV Score 계산 — 균등 합산 vs 지수 감쇠 vs 일평균

### 고민의 시작

주간/월간 랭킹을 MV 테이블에 적재할 때, score를 어떻게 계산할 것인가?

Redis monthly는 이미 지수 감쇠(`daily × 0.97^i`)로 "최근 트렌드"를 반영하고 있다. MV도 같은 방식을 써야 하는가, 다른 방식을 써야 하는가?

### 검토한 3가지 방식

**방식 A: 균등 합산 (채택)**

```
score = f(SUM(30일 메트릭))
```

- 30일간 메트릭을 단순 합산 후 score 공식 1회 적용
- "기간 총 실적"을 공정하게 평가
- 30일 전이나 오늘이나 동등한 가중치

**방식 B: 지수 감쇠**

```
monthly = Σ(i=0 ~ 29) daily_score × 0.97^i
```

- 최근 데이터에 높은 가중치 (반감기 약 23일)
- Redis monthly와 유사한 성격
- "최근에 뜨는 상품"을 우대

**방식 C: 일평균**

```
score = f(SUM(메트릭) / COUNT(DISTINCT 전시일수))
```

- 전시 기간에 관계없이 "일당 성과"로 비교
- 전시 기간 편향 보정 가능
- 표본 크기 문제 (1일만 전시된 상품이 과대평가)

### 핵심 트레이드오프

#### 균등 합산을 채택한 이유: 공개 랭킹 보드의 비즈니스 의미

**"이번 달 베스트셀러"는 총 판매량 기준이 이커머스 업계 표준이다.**

쿠팡, 무신사, 교보문고 등 주요 이커머스의 공개 랭킹 보드는 기간 총 실적 기준으로 운영된다. 소비자가 "인기 상품 TOP 100"을 볼 때 기대하는 것은 "가장 많이 팔린 상품"이지, "일평균 판매량이 높은 상품"이나 "최근에 급등한 상품"이 아니다.

균등 합산은 이 비즈니스 의미에 정확히 부합한다:
- **MD/상품기획팀의 관점**: "이번 달 어떤 상품이 가장 많이 팔렸나?" → 총 실적
- **소비자의 관점**: "다들 뭘 사고 있나?" → 총 판매량 순위
- **경영진의 관점**: "매출 기여도가 높은 상품은?" → 총 매출 기준

#### 지수 감쇠를 선택하지 않은 이유: Lambda Architecture에서의 역할 분담

**"MV가 Redis와 같은 결과를 내면, MV를 만들 이유가 없다."**

| 관점 | Redis (지수 감쇠) | MV (균등 합산) |
|------|------------------|---------------|
| 비즈니스 의미 | "지금 뜨는 상품" (트렌드) | "이번 달 베스트셀러" (누적 성과) |
| 소비자 시나리오 | 메인 페이지 실시간 인기 | 카테고리별 베스트, 기간별 랭킹 |
| 사업자 시나리오 | 실시간 모니터링 | 주간/월간 리포트, MD 성과 분석 |

두 시스템이 다른 관점을 제공하는 것이 Lambda Architecture에서 Speed Layer와 Batch Layer의 역할 분담이다. Speed Layer(Redis)가 이미 트렌드를 반영하고 있으므로, Batch Layer(MV)는 "정확한 기간 집계"에 집중하는 것이 아키텍처적으로 맞다.

숫자로 검증한 결과, 감쇠를 쓰더라도 월간이 일간/주간과 비슷해지지는 않는다. 하지만 감쇠를 쓸 이유가 없는 것이 핵심이다. Redis가 이미 하고 있는 일을 MV에서 반복하면 두 시스템의 결과가 수렴하고, MV의 존재 가치가 떨어진다.

```
상품 A: 30일간 매일 매출 100만원 (꾸준)
상품 B: 최근 5일간 매일 600만원 (급등), 나머지 0원
총 실적: 둘 다 3000만원

              일간     주간(균등)   주간(감쇠)   월간(균등)   월간(감쇠)
상품 A        0.600    0.693       4.09        0.735       12.0
상품 B        0.678    0.735       3.33        0.735        3.33
승자          B        B           A           동점         A 압승
```

#### 일평균을 선택하지 않은 이유: 공개 랭킹의 목적과 불일치

"전시 기간 편향"은 실제 운영에서 존재하는 문제다. 30일 전시된 상품이 3일 전시된 신상품보다 누적 실적이 높은 것은 당연하고, 이것이 "인기"를 정확히 반영하는가는 논쟁의 여지가 있다.

그러나 일평균으로 전환하면 **공개 랭킹 보드로서의 비즈니스 목적과 충돌**한다:

```
상품 A: 전시 30일, 총 매출 3000만원, 일평균 100만원
상품 B: 전시 3일,  총 매출 900만원,  일평균 300만원

일평균 기준: B 승 (300만 > 100만)
총 실적 기준: A 승 (3000만 > 900만)
```

- **MD팀이 원하는 "이번 달 베스트"는 A다.** 총 매출 3000만원 상품이 1위에 있어야 매출 기여도 분석이 된다
- B가 1위가 되면 **"3일 만에 900만원 판 신상품이 베스트셀러"**라는 오해를 줄 수 있다
- 표본 크기 문제: 1일 전시에 매출 500만원이면 일평균 500만원으로 A보다 위에 올라간다

**전시 기간 편향을 보정하려면 일평균보다 더 적합한 방법이 있다:**

| 방법 | 적합한 시스템 | 이유 |
|------|-------------|------|
| **일평균** | 내부 분석 리포트, MD 대시보드 | "상품의 판매 효율"을 보려면 적합. 하지만 공개 랭킹의 지표는 아님 |
| **노출 대비 전환율** (order/view) | 개인화 추천 시스템 | "이 상품을 본 사람 중 몇 %가 샀는가"는 상품의 매력도를 측정. 하지만 공개 랭킹에 쓰면 조회 500회 전환율 10% 상품이 조회 100만회 전환율 0.5% 상품 위에 올라감 — 소비자가 기대하는 "인기 상품"이 아님 |
| **총 실적 (현행)** | 공개 랭킹 보드 | "가장 많이 팔린 상품" = 소비자와 사업자 모두 직관적으로 이해 |

전환율이 유용한 곳은 **추천 시스템**이다. "이 사용자에게 어떤 상품을 노출할까?"를 결정할 때 전환율이 높은 상품을 추천하면 구매 확률이 높아진다. 이것은 개인화된 추천 영역이지, 전체 사용자에게 동일하게 보여주는 공개 랭킹과는 목적이 다르다. 다만 실제 이커머스에서 공개 랭킹의 내부 score 공식에 전환율을 보조 가중치로 섞을 가능성은 있다. 핵심은 **primary 지표가 전환율인 공개 랭킹은 없다**는 것이다.

### 선택하지 않은 대안에서 배운 것

- 지수 감쇠를 검토하면서 **"같은 데이터로 다른 관점을 제공하는 것"**이 Lambda Architecture에서 Batch Layer의 존재 가치임을 이해했다
- 일평균을 검토하면서 **"공정한 비교"와 "비즈니스 의미" 사이의 긴장**을 인식했다. 수학적으로 공정한 것과 비즈니스적으로 의미 있는 것은 다를 수 있다
- 전환율을 검토하면서 **"같은 지표가 시스템 목적에 따라 다른 위치에 놓인다"**는 것을 이해했다. 전환율은 추천 시스템에서는 핵심 지표이지만, 공개 랭킹에서는 보조 지표다

### 지금 다시 한다면?

균등 합산을 유지하되, **일평균을 별도 컬럼으로 MV에 함께 저장**할 것이다. `avg_daily_sales = total_sales / active_days`를 MV에 추가하면, MD 대시보드에서 "판매 효율 기준 정렬"을 제공할 때 재집계 없이 확장 가능하다. 공개 랭킹의 정렬 기준은 총 실적을 유지하면서, 내부 분석용으로 일평균 데이터를 함께 제공하는 것이 실운영에서 가장 실용적인 접근이다.

---

## 소재 2: 슬라이딩 윈도우 vs 캘린더 윈도우

### 고민

주간/월간 랭킹의 기간을 어떻게 잡을 것인가?

| 전략 | 예시 | 갱신 주기 |
|------|------|----------|
| 캘린더 | 주간: 월~일, 월간: 1일~말일 | 주 1회, 월 1회 |
| 슬라이딩 | 오늘 기준 최근 7일/30일 | 매일 |

### 슬라이딩을 선택한 이유

1. **Redis weekly와 시간 범위 일치**: Redis ZUNIONSTORE가 "최근 7일 daily"를 합산하는 슬라이딩 방식. MV가 캘린더이면 Redis fallback 시 시간 범위가 불일치하여 랭킹 결과의 연속성이 깨짐
2. **이커머스 업계 관행**: 무신사, 쿠팡 등에서 주간/월간 랭킹을 매일 갱신. "주간 인기 상품"이 월요일에만 바뀌면 사용자가 매일 같은 랭킹을 보게 되어 재방문 유인이 떨어짐
3. **배치 비용 대비 효과**: GROUP BY + TOP 100 INSERT는 상품 수만 건 기준 수초 내 완료. 매일 실행해도 시스템 부하가 미미하며, 사용자에게 매일 갱신되는 랭킹을 제공하는 효과가 큼
4. **운영 단순성**: period_key가 targetDate(`20260416`) 자체이므로 "이 날짜 기준 최근 N일"이라는 명확한 의미. 캘린더 방식은 ISO 주차(`2026-W16`)나 월(`2026-04`) 계산이 필요하고, 월말/주초 경계 처리가 복잡

### 캘린더 방식이 더 적합한 경우

캘린더를 기각했지만, 다음 상황에서는 캘린더가 맞다:
- **정산/리포팅 시스템**: "4월 매출 정산"은 4/1~4/30 고정 기간이어야 한다. 슬라이딩이면 기준일에 따라 금액이 달라져 정산 불일치
- **마케팅 캠페인 성과 분석**: "이번 주 프로모션 효과"는 캠페인 시작~종료 고정 기간 기준
- **배치 비용이 높은 경우**: 수억 건 집계에 수십 분 걸리면 매일 실행이 부담

우리 과제는 정산이 아닌 **소비자 대상 랭킹 보드**이므로 슬라이딩이 적합하다.

---

## 소재 3: Chunk vs Tasklet — 언제 무엇을 쓰는가

### Spring Batch가 Chunk-Oriented에 제공하는 운영 기능

Chunk-Oriented는 단순히 "Reader → Processor → Writer"의 패턴이 아니다. Spring Batch 프레임워크가 Chunk에 대해 제공하는 **운영 레벨의 기능**이 Chunk를 보편적 선택으로 만드는 핵심이다.

#### 1. 자동 Retry (Transient Failure 재시도)

```java
@Bean
public Step step() {
    return new StepBuilder("step", jobRepository)
        .<In, Out>chunk(1000, transactionManager)
        .reader(reader())
        .processor(processor())
        .writer(writer())
        .faultTolerant()
        .retry(DeadlockLoserDataAccessException.class)   // DB 데드락 시 재시도
        .retry(OptimisticLockingFailureException.class)   // 낙관적 락 충돌 시 재시도
        .retryLimit(3)                                     // 최대 3회
        .build();
}
```

대규모 이커머스에서 배치가 수백만 건을 처리하는 동안 **일시적 DB 데드락, 네트워크 타임아웃**이 발생할 수 있다. Chunk는 해당 chunk만 재시도하고, Tasklet에서는 이 로직을 직접 구현해야 한다.

#### 2. Skip Policy (불량 레코드 건너뛰기)

```java
.faultTolerant()
.skip(DataIntegrityViolationException.class)  // PK 중복 등 → 건너뛰기
.skipLimit(100)                                // 최대 100건까지 허용
.noSkip(OutOfMemoryError.class)               // OOM은 절대 건너뛰지 않음
```

100만 건 중 3건의 데이터 오류 때문에 전체 배치가 실패하면 운영 부담이 크다. Skip Policy로 불량 레코드를 건너뛰고 나머지를 계속 처리할 수 있다. Tasklet의 SQL 한 방에서는 1건의 에러가 전체를 롤백시킨다.

#### 3. Restart (실패 지점부터 재시작)

```
최초 실행:
  Chunk 1: 1~1,000건     ✓ 커밋 완료
  Chunk 2: 1,001~2,000건 ✓ 커밋 완료
  Chunk 3: 2,001~3,000건 ✗ 실패 (DB 커넥션 에러)
  → ExecutionContext에 진행 상태 저장

재시작:
  Chunk 1~2: 건너뜀 (이미 커밋됨)
  Chunk 3: 2,001~3,000건부터 재시작
```

수시간 걸리는 배치가 80% 진행 후 실패하면, 처음부터 재실행하는 것은 비용이 크다. Chunk는 Spring Batch의 메타 테이블(`BATCH_STEP_EXECUTION_CONTEXT`)에 진행 상태를 저장하여 실패 지점부터 재시작할 수 있다.

#### 4. 자동 모니터링 (처리 건수 추적)

```
StepExecution 자동 기록:
  - readCount: 읽은 건수
  - writeCount: 쓴 건수
  - skipCount: 건너뛴 건수
  - commitCount: 커밋 횟수
  - rollbackCount: 롤백 횟수
  - readSkipCount / writeSkipCount / processSkipCount
```

Tasklet에서는 이 지표들을 직접 카운팅하고 로깅해야 한다. Chunk는 Spring Batch가 자동으로 기록하고, `BATCH_STEP_EXECUTION` 테이블에서 조회할 수 있다.

#### 5. Listener 기반 확장

```java
.listener(new ItemReadListener<>() {
    public void onReadError(Exception ex) { alertService.send("Reader 에러: " + ex); }
})
.listener(new ItemWriteListener<>() {
    public void afterWrite(Chunk<? extends Out> items) { metrics.increment("batch.write", items.size()); }
})
```

읽기/쓰기/처리 각 단계에 Listener를 붙여 모니터링, 알림, 메트릭 수집을 할 수 있다.

### Chunk가 보편적 선택인 이유

위 기능들은 **프레임워크가 무료로 제공하는 것**이다. Tasklet으로 동일한 수준의 운영 안정성을 확보하려면 retry 루프, skip 카운터, 진행 상태 저장, 처리 건수 추적을 모두 직접 구현해야 한다. 대부분의 배치 작업에서 이 운영 기능의 가치가 네트워크 왕복의 비용보다 크기 때문에 Chunk가 보편적 선택이 된다.

### Tasklet이 Chunk보다 효율적인 경우

그럼에도 Tasklet이 맞는 **특정 조건**이 있다:

| 조건 | 설명 | 예시 |
|------|------|------|
| **SQL 한 문장으로 완결** | Java 변환이 전혀 없고 DB→DB 이동 | `INSERT INTO...SELECT...GROUP BY` |
| **retry/skip이 불필요** | 실패 시 전체 재실행해도 수초 내 완료 | TOP 100 적재 (100건 INSERT) |
| **중간 상태가 없음** | 처리 중 실패해도 "부분 완료" 상태가 의미 없음 | DELETE + INSERT 패턴 (어차피 전체 교체) |

실무 배치 앱 분석에서 관찰한 통계/집계 Job이 Tasklet을 쓰는 것은 **이 세 조건을 모두 충족하기 때문**이지, Tasklet이 일반적으로 우월하기 때문이 아니다.

### 이 작업에서의 판단

우리의 MV TOP 100 적재는 Tasklet의 세 조건을 모두 충족한다:
- SQL 한 문장(INSERT INTO...SELECT + RANK() + LIMIT 100)으로 완결 가능
- 100건 INSERT는 수초 내 완료 → 실패 시 전체 재실행해도 부담 없음
- DELETE + INSERT 패턴이므로 부분 완료 상태가 의미 없음

**그러나 Chunk로 구현하면서 프레임워크의 운영 기능을 활용하는 것도 합리적이다:**
- `.faultTolerant().retry()`로 일시적 DB 에러에 대한 자동 재시도
- `StepExecution`의 read/write count로 자동 모니터링
- `StepMonitorListener`와 결합하여 실패 시 알림

Chunk의 네트워크 왕복 비용(100건 × ~10KB < 1ms)보다 이 운영 기능의 가치가 크므로, **Chunk를 쓰되 Reader SQL에서 비효율을 최소화하는 것**이 우리의 접근이다.

### 실무 배치 프로젝트에서는 이 운영 기능을 쓰고 있는가?

실무 배치 앱 2개(Spring Boot 3.3.4 + Batch 5.x, 총 90개 Job)를 분석한 결과:

| 운영 기능 | 사용 여부 |
|----------|----------|
| `.faultTolerant()` | ❌ 없음 |
| `.retry()` / `retryLimit` | ❌ 없음 |
| `.skip()` / `skipLimit` | ❌ 없음 |
| `ItemReadListener` / `ItemWriteListener` | ❌ 없음 |
| `ChunkListener` / `SkipListener` | ❌ 없음 |
| `allowStartIfComplete` (restart) | ❌ 없음 |

**90개 Job 중 단 하나도 retry, skip, restart를 사용하지 않는다.**

사용하는 Listener는 딱 2종류:
- `SingleJobExecutionListener` — 중복 실행 방지 (JobExecutionListener)
- `StepExecutionListener` — 검색 인덱스 Job 2개에서 다른 배치 실행 중인지 체크

이것은 **retry/skip 없이도 실무 운영이 가능하다**는 뜻이다. 그러나 좋은 설계인지는 별개의 문제다:
- retry 없이 운영 = 1건의 일시적 DB 에러가 전체 배치를 실패시킴
- skip 없이 운영 = 1건의 데이터 오류가 나머지 수만 건의 처리를 막음
- 이것은 **운영 리스크를 감수하는 것**이지, 모범 사례가 아니다

우리 프로젝트에서는 이 부분을 개선하여 `faultTolerant + retry`를 적용한다. 실무에서 빠져 있는 것을 보완하는 것도 의미 있는 설계 판단이다.

### 코드 레벨 비교: Chunk vs Tasklet

#### Tasklet 방식 (SQL 중심)

```java
@Configuration
@RequiredArgsConstructor
public class ProductRankingMvTaskletJobConfig {

    public static final String JOB_NAME = "productRankingMvJob";
    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JdbcTemplate jdbcTemplate;

    @Bean(JOB_NAME)
    public Job productRankingMvJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(cleanupStep()).on("FAILED").end()
            .from(cleanupStep()).on("*").to(aggregateStep())
            .end()
            .build();
    }

    @Bean
    @JobScope
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                String scope = chunkContext.getStepContext()
                    .getJobParameters().get("scope").toString();
                String targetDate = chunkContext.getStepContext()
                    .getJobParameters().get("targetDate").toString();
                String table = "weekly".equals(scope)
                    ? "mv_product_rank_weekly" : "mv_product_rank_monthly";
                jdbcTemplate.update(
                    "DELETE FROM " + table + " WHERE period_key = ?", targetDate);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }

    @Bean
    @JobScope
    public Step aggregateStep() {
        return new StepBuilder("aggregateStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                String scope = chunkContext.getStepContext()
                    .getJobParameters().get("scope").toString();
                String targetDate = chunkContext.getStepContext()
                    .getJobParameters().get("targetDate").toString();
                String table = "weekly".equals(scope)
                    ? "mv_product_rank_weekly" : "mv_product_rank_monthly";
                int days = "weekly".equals(scope) ? 6 : 29;

                jdbcTemplate.update("""
                    INSERT INTO %s
                        (product_id, ranking, score, view_count, like_count,
                         sales_count, sales_amount, period_key)
                    SELECT product_id, DT_RNK, score,
                           total_view_count, total_net_like_count,
                           total_sales_count, total_net_sales_amount, ?
                    FROM (
                        SELECT pm.product_id,
                            SUM(pm.view_count) AS total_view_count,
                            SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
                            SUM(pm.sales_count) AS total_sales_count,
                            SUM(pm.sales_amount - pm.cancel_amount_by_event_date)
                                AS total_net_sales_amount,
                            (0.1 * LOG10(GREATEST(SUM(pm.view_count),0)+1) / 7.0
                           + 0.2 * LOG10(GREATEST(SUM(pm.like_count - pm.unlike_count),0)+1) / 7.0
                           + 0.7 * LOG10(GREATEST(SUM(pm.sales_amount
                               - pm.cancel_amount_by_event_date),0)+1) / 7.0
                           + UNIX_TIMESTAMP() * 1e-16) AS score,
                            RANK() OVER (ORDER BY
                                (0.1 * LOG10(GREATEST(SUM(pm.view_count),0)+1) / 7.0
                               + 0.2 * LOG10(GREATEST(SUM(pm.like_count - pm.unlike_count),0)+1) / 7.0
                               + 0.7 * LOG10(GREATEST(SUM(pm.sales_amount
                                   - pm.cancel_amount_by_event_date),0)+1) / 7.0
                               + UNIX_TIMESTAMP() * 1e-16) DESC) AS DT_RNK
                        FROM product_metrics pm
                        JOIN product p ON pm.product_id = p.id
                        WHERE pm.metric_date BETWEEN DATE_SUB(STR_TO_DATE(?, '%%Y%%m%%d'),
                            INTERVAL %d DAY) AND STR_TO_DATE(?, '%%Y%%m%%d')
                          AND p.deleted_at IS NULL
                        GROUP BY pm.product_id
                    ) ranked
                    WHERE DT_RNK <= 100
                    """.formatted(table, days),
                    targetDate, targetDate, targetDate);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .build();
    }
}
```

- **장점**: 네트워크 왕복 0. 코드가 짧다. SQL 한 문장으로 집계+정렬+적재 완료
- **단점**: retry/skip 없음. SQL이 비대함. score 공식 단위 테스트 불가

#### Chunk 방식 (우리 구현)

```java
@Configuration
@RequiredArgsConstructor
public class ProductRankingMvJobConfig {

    public static final String JOB_NAME = "productRankingMvJob";
    private static final int CHUNK_SIZE = 100;

    @Bean(JOB_NAME)
    public Job productRankingMvJob(Step cleanupStep, Step aggregateStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(cleanupStep).on("FAILED").end()
            .from(cleanupStep).on("*").to(aggregateStep)
            .end()
            .listener(jobListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<RankedProductRow> mvMetricsReader(
            @Value("#{jobParameters['targetDate']}") String targetDate,
            @Value("#{jobParameters['scope']}") String scope) {
        int days = "weekly".equals(scope) ? 6 : 29;
        return new JdbcCursorItemReaderBuilder<RankedProductRow>()
            .name("mvMetricsReader")
            .dataSource(dataSource)
            .sql("""
                SELECT pm.product_id, ... ,
                    (0.1 * LOG10(...) + ...) AS score
                FROM product_metrics pm
                JOIN product p ON pm.product_id = p.id
                WHERE pm.metric_date BETWEEN ? AND ?
                  AND p.deleted_at IS NULL
                GROUP BY pm.product_id
                ORDER BY score DESC
                LIMIT 100
                """)
            .preparedStatementSetter(ps -> { /* 날짜 파라미터 바인딩 */ })
            .rowMapper((rs, rowNum) -> new RankedProductRow(...))
            .build();
    }

    @Bean
    public Step aggregateStep(JdbcCursorItemReader<RankedProductRow> reader,
                              ItemWriter<RankedProductRow> writer) {
        return new StepBuilder("aggregateStep", jobRepository)
            .<RankedProductRow, RankedProductRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(reader)
            .processor(rankingProcessor())      // ranking 번호 부여
            .writer(writer)
            .faultTolerant()
                .retry(DeadlockLoserDataAccessException.class)
                .retryLimit(3)
            .listener(stepMonitorListener)
            .build();
    }
}
```

- **장점**: retry로 일시적 DB 에러 자동 재시도. StepExecution에 read/write count 자동 기록. StepMonitorListener로 실패 시 알림
- **단점**: 네트워크 왕복 2회 (100건, < 1ms). Processor가 ranking 부여만 하므로 역할이 가벼움

---

## 소재 4: Redis(Speed Layer) vs MV(Batch Layer) — Lambda Architecture 실전

### 핵심 질문

"Redis에서 이미 주간/월간 랭킹을 제공하고 있는데, 왜 MV를 또 만드는가?"

### 운영 관점의 답

| 관점 | Redis (Speed Layer) | MV (Batch Layer) |
|------|---------------------|-------------------|
| **정확도** | carry-over 근사치 (일별 score 합산, 지수 감쇠) | DB 원장 기반 정확값 (메트릭 균등 합산) |
| **장애 내성** | Redis 다운 → 주간/월간 조회 불가 | DB만 살아있으면 조회 가능. Redis 장애 시 fallback |
| **데이터 관점** | "지금 뜨는 상품" (트렌드) | "기간 총 실적" (누적 성과) |
| **비즈니스 용도** | 메인 페이지 실시간 인기 | 기간별 베스트셀러, MD 리포트, 정산 참고 |
| **데이터 검증** | Redis 내부 데이터 확인 어려움 | SQL로 즉시 검증 가능 |

### log₁₀ 비선형성이 만드는 실제 차이

```
상품 X: 7일간 view = [100, 100, 100, 100, 100, 100, 100] (총 700)
상품 Y: 7일간 view = [0, 0, 0, 0, 0, 0, 700] (총 700)

Redis (일별 score 합산):
  X: 7 × log₁₀(101)/7 = 2.003
  Y: 6 × 0 + log₁₀(701)/7 = 0.406
  → X 압도적 유리 (꾸준한 상품 우대)

MV (메트릭 합산 후 score):
  X: log₁₀(701)/7 = 0.406
  Y: log₁₀(701)/7 = 0.406
  → 동점 (총 활동량 동일)
```

이 차이가 "두 시스템이 공존하는 이유"이며, Lambda Architecture에서 Speed Layer와 Batch Layer가 다른 특성을 갖는 것은 설계 의도다. 같은 원천 데이터(product_metrics)에서 출발하지만, 계산 방식의 차이로 다른 관점의 랭킹을 제공한다.

실운영에서 이 차이는 **"Redis 랭킹과 MV 랭킹이 왜 다르냐?"**는 CS 문의로 이어질 수 있다. 이를 위해 두 시스템의 차이를 문서화하고, API 응답에 `source: "mv"` 또는 `source: "redis"` 필드를 포함하여 어느 소스에서 제공한 랭킹인지 투명하게 노출하는 것이 운영상 바람직하다.

---

## 소재 5: Score 계산과 TOP-N 필터링 — DB에서 하는가, Java에서 하는가

### 고민의 시작

Chunk-Oriented 배치에서 "전체 상품의 score를 계산하고 TOP 100만 MV에 적재"해야 한다. 이 로직을 어디에 배치하느냐에 따라 효율이 크게 달라진다.

### 검토한 방안

| 방안 | Reader | Processor | Writer | 비효율 포인트 |
|------|--------|-----------|--------|-------------|
| **A. Java 전체 처리** | 전체 조회 (수만 건) | score 계산 | 정렬 + TOP 100 INSERT | 수만 건을 Java로 읽어와서 정렬/필터링 — DB가 이미 최적화된 작업을 애플리케이션에서 반복 |
| **B. 전체 INSERT 후 삭제** | 전체 조회 | score 계산 | 전체 INSERT → Step 3에서 100위 밖 DELETE | 수만 건 INSERT 후 대부분 삭제 — 불필요한 I/O |
| **C. SQL에서 완료 (채택)** | GROUP BY + score + ORDER BY + LIMIT 100 → **100건만 반환** | ranking 부여 | 100건 INSERT | DB가 집계, 계산, 정렬, 필터링을 한 번에 처리 |

### 방안 C가 효율적인 이유: SQL 실행 순서

```
1. FROM / JOIN    → product_metrics × product 조인
2. WHERE          → 날짜 범위 필터
3. GROUP BY       → product_id별 그룹핑 + SUM 집계
4. SELECT         → score 계산 (LOG10 등 수학 함수)
5. ORDER BY       → score 내림차순 정렬 (전체 상품 대상)
6. LIMIT 100      → 상위 100건만 반환
```

DB가 **전체 상품의 score를 계산하고 정렬한 후** 상위 100건만 네트워크로 전달한다. Reader는 100건만 받지만, 그 100건이 score 기준 TOP 100인 것은 DB가 보장한다.

Reader SQL:

```sql
SELECT
    pm.product_id,
    SUM(pm.view_count) AS total_view_count,
    SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
    SUM(pm.sales_count) AS total_sales_count,
    SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
    (
        0.1 * LOG10(GREATEST(SUM(pm.view_count), 0) + 1) / 7.0
      + 0.2 * LOG10(GREATEST(SUM(pm.like_count - pm.unlike_count), 0) + 1) / 7.0
      + 0.7 * LOG10(GREATEST(SUM(pm.sales_amount - pm.cancel_amount_by_event_date), 0) + 1) / 7.0
      + UNIX_TIMESTAMP() * 1e-16
    ) AS score
FROM product_metrics pm
JOIN product p ON pm.product_id = p.id
WHERE pm.metric_date BETWEEN :startDate AND :endDate
  AND p.deleted_at IS NULL
GROUP BY pm.product_id
ORDER BY score DESC
LIMIT 100
```

### 회사 배치 앱에서의 검증

회사 코드를 분석한 결과, **score 계산 + TOP-N 필터링을 SQL에서 처리하는 것이 실무 표준**이었다:

**GoodsBestMapper.xml** — 상품 베스트 TOP 100:

```sql
RANK() OVER (ORDER BY SUM(ORD_QTY) DESC) AS DT_RNK
...
WHERE DT_RNK <= 100
```

- Java(GoodsBestServiceImpl)는 파라미터만 전달. 랭킹 로직 없음
- DELETE → INSERT 패턴. SQL이 모든 계산을 처리

**GoodsNewMapper.xml** — 카테고리별 신상품 TOP 50:

```sql
DENSE_RANK() OVER (PARTITION BY DISP_CTG_NO ORDER BY SYS_REG_DTM DESC) AS DT_RNK
WHERE DT_RNK <= 50
```

**EtEntrEvltAgrtTrxMapper.xml** — 입점사 매출 상위 10%:

```sql
PERCENT_RANK() OVER (ORDER BY SUM(ORD_AMT - CNCL_AMT) DESC) AS PERCENT_RNK
WHERE PERCENT_RNK <= 0.1
```

**12개 매퍼에서 `RANK()`, `DENSE_RANK()`, `ROW_NUMBER()`, `PERCENT_RANK()` 윈도우 함수 사용.** Java에서 랭킹/스코어링을 처리하는 배치 Job은 없었다.

### 트레이드오프: Score 공식의 이중 관리

SQL에 score 공식을 넣으면, RankingCorrectionJob(Java)과 MV Job(SQL)에 같은 공식이 두 곳에 존재한다.

| 관점 | 분석 |
|------|------|
| **왜 허용 가능한가** | 두 Job은 입력이 다르다. RankingCorrectionJob은 **일간 메트릭**(CURDATE() 1일)을 읽고, MV Job은 **기간 합산 메트릭**(7일/30일 SUM)을 읽는다. 같은 공식이지만 적용 대상이 다르므로 하나의 Java 메서드를 공유하는 것이 오히려 부자연스럽다 |
| **변경 시 위험** | 가중치(0.1/0.2/0.7)나 MAX_LOG(7.0) 변경 시 두 곳 모두 수정 필요. 하지만 가중치는 `application.yml`에 정의되어 있으므로, SQL에서도 파라미터로 주입 가능 |
| **회사 코드 참고** | 회사는 score 공식이 SQL에만 존재(Java에 없음). 우리 프로젝트는 RankingCorrectionJob이 이미 Java에 공식을 가지고 있어서 이중 관리가 발생하지만, 이것은 두 Job의 역할이 다르기 때문에 합리적인 중복이다 |

### 이 판단에서 배운 것

- **"어디서 계산하느냐"는 효율의 문제이지 패턴의 문제가 아니다.** Chunk-Oriented에서 Processor가 비즈니스 로직을 담당해야 한다는 것은 일반론이지, 모든 경우에 적용해야 하는 규칙이 아니다
- **DB가 잘하는 일(집계, 정렬, 필터링)은 DB에서 끝내야 한다.** 수만 건을 Java로 읽어와서 정렬하는 것은 DB가 이미 최적화된 실행 계획으로 한 번에 처리할 수 있는 일을 애플리케이션에서 반복하는 것이다
- **회사 코드가 이 판단을 뒷받침한다.** 12개 매퍼에서 윈도우 함수로 TOP-N을 처리하고, Java는 오케스트레이션만 하는 것이 이 회사의 실무 표준이다

---

## 소재 6: 사전 집계 파이프라인과 Chunk의 관계

### 핵심 통찰: 사전 집계는 입력을, Chunk는 출력을 다룬다

```
사전 집계 파이프라인 (Kafka → Flink/Spark → product_metrics):
  수억 건 이벤트 → 일간 집계 테이블
  → Reader의 입력 볼륨을 줄이는 것

Chunk-Oriented:
  대량의 행을 chunk 단위로 읽고-변환하고-적재
  → Writer의 출력 볼륨이 클 때 + 운영 안정성이 필요할 때 가치가 있는 것

둘은 서로 다른 문제를 해결한다.
```

사전 집계가 있어야 Chunk가 유용한 것이 아니다. Chunk의 가치는 **"프레임워크가 제공하는 retry, skip, restart, 모니터링을 활용하면서 대량의 행을 안정적으로 처리할 때"** 발휘된다.

### 대규모 이커머스에서의 DB 부하 문제

쿠팡급(상품 100만, product_metrics 30일치 3,000만 행) 기준으로, Chunk든 Tasklet이든 **집계 쿼리의 DB 부하는 동일하다.** 진짜 해결해야 할 문제는 처리 모델 선택이 아니라 **"이 집계를 서비스 DB에서 할 것인가"**이다. 답은 Replica DB 또는 DW에서 집계하는 것이고, 이것은 두 방식 모두에 적용된다.

### 우리의 접근

Chunk를 쓰되, Reader SQL에서 GROUP BY + score 계산 + ORDER BY + LIMIT 100까지 처리하여 **Java로 넘어오는 데이터를 100건으로 제한**했다. Chunk의 네트워크 왕복 비용(100건 × ~10KB < 1ms)보다 프레임워크가 제공하는 운영 기능(retry, 모니터링, restart)의 가치가 크므로, Chunk 선택은 합리적이다.

---

## 소재 7: Best Practice 대조 — 이론 vs 우리 설계 vs 배치 프로젝트 분석

### Spring Batch Chunk Best Practice를 3가지 관점에서 비교

| Best Practice | 이론적 권장 | 우리 설계 | 배치 프로젝트 분석 (90개 Job) |
|-------------|-----------|----------|--------------------------|
| **faultTolerant + retry** | 일시적 에러(데드락, 타임아웃) 자동 재시도. ExponentialBackOffPolicy로 간격 확보 | ✅ 적용. retry(3) + ExponentialBackOffPolicy | ❌ 90개 Job 전부 미사용. 1건 에러 = 전체 실패 |
| **skip policy** | 데이터 오류 시 건너뛰고 나머지 처리. SkipListener로 누락 추적 필수 | 미적용 (의도적). 100건이므로 skip 시 chunk scan(100번 재실행) 비용이 더 큼 | ❌ 미사용 |
| **ExponentialBackOffPolicy** | retry 시 100ms→200ms→400ms 간격. 즉시 재시도는 데드락 상태에서 반복 실패 | ✅ 적용 | ❌ retry 자체가 없으므로 해당 없음 |
| **Writer 벌크 처리** | JdbcBatchItemWriter로 JDBC batch INSERT. 개별 INSERT 루프는 안티패턴 | ✅ JdbcBatchItemWriter 사용 | ⚠️ Chunk Job(2개)은 MyBatisBatchItemWriter 사용. 하지만 GoodsReviewTotal은 행 단위 UPSERT 루프 (안티패턴) |
| **Cursor vs Paging 선택** | 단일 스레드 순차 → Cursor. 멀티스레드/재시작 → Paging | ✅ JdbcCursorItemReader (단일 스레드). 병렬화 시 전환 필요 명시 | Cursor(2개), Paging(2개) 혼용 |
| **Processor에서 DB 수정 금지** | 쓰기는 Writer에서만. 트랜잭션 경계 명확화 | ✅ Processor는 ranking 부여만 | ✅ 준수 (Processor에서 DB 수정하는 Job 없음) |
| **cleanupStep allowStartIfComplete** | 멱등한 Step은 재시작 시에도 항상 실행 허용 | ✅ 적용 | ❌ 해당 설정 없음 |
| **saveState(false)** | 재시작 불필요한 Step은 상태 저장 오버헤드 제거 | 미적용. 100건이므로 오버헤드 무시 가능 | ❌ 해당 설정 없음 |
| **SkipListener.onSkipInWrite** | skip된 아이템을 별도 기록하여 데이터 정합성 추적 | 해당 없음 (skip 미적용) | ❌ skip 자체가 없음 |
| **StepExecutionListener** | Step 시작/종료/실패 시 모니터링, 알림 | ✅ StepMonitorListener (기존 인프라) | ⚠️ 2개 Job에서만 사용 (검색 인덱스). 나머지 88개 Job은 미사용 |

### 이 비교에서 드러나는 것

**배치 프로젝트 90개 Job이 retry/skip/restart를 하나도 쓰지 않는다는 것은 주목할 만하다.** 이것은 두 가지로 해석할 수 있다:

1. **"운영에서 문제가 없었다"**: 배치 데이터가 안정적이고, 실패 빈도가 낮아서 전체 재실행으로 충분히 대응 가능했을 수 있다. 실제로 통계/집계 Job은 대부분 수초~수분 내 완료되므로 전체 재실행 비용이 낮다.

2. **"운영 리스크를 감수하고 있다"**: 1건의 일시적 에러가 전체 배치를 실패시키는 구조다. 야간 배치가 데드락으로 실패하면 아침에 출근해서 수동 재실행해야 한다. retry를 걸어두면 자동으로 복구됐을 에러다.

**우리 프로젝트에서 retry + ExponentialBackOffPolicy를 적용하는 것은, 배치 프로젝트에서 빠져 있는 운영 안정성을 보완하는 설계 판단이다.** "남들이 안 쓰니까 안 써도 된다"가 아니라, "프레임워크가 제공하는 운영 기능을 활용하여 야간 배치의 자동 복구 가능성을 높인다"는 근거다.

### Writer skip 시 chunk scan 문제

Best Practice에서 중요한 경고: **Writer에서 skip이 발생하면 해당 chunk 전체가 롤백되고, 아이템을 1개씩 재실행하는 "chunk scan"이 발생한다.** chunkSize=1000이면 최악의 경우 1000번 개별 실행.

이것이 우리가 skip을 적용하지 않는 이유 중 하나다. 100건에서 skip이 발생하면 100번 개별 INSERT가 실행되는데, 벌크 INSERT의 이점이 사라진다. 100건이라 성능 차이는 미미하지만, skip의 목적(불량 레코드 건너뛰기)이 이 시나리오에서 의미가 없다 — 100건 모두 같은 SQL로 계산된 결과이므로, 1건이 실패하면 SQL 자체의 문제이지 데이터 오류가 아니다.

---

## 소재 8: CursorReader vs PagingReader — GROUP BY 집계 쿼리에서의 선택

### 핵심: PagingReader는 GROUP BY 집계 쿼리에서 치명적이다

PagingReader는 페이지마다 **독립된 쿼리를 재실행**한다. 단순 WHERE + ORDER BY 쿼리에서는 문제없지만, GROUP BY가 포함된 집계 쿼리에서는 **매 페이지마다 전체 데이터를 다시 집계**한다:

```
CursorReader:
  GROUP BY 3,000만 행 → 1번 실행 → 결과 스트리밍
  총 집계 실행: 1회

PagingReader (pageSize=1000, 상품 100만 건 = 1,000페이지):
  페이지 1: GROUP BY 3,000만 행 → 정렬 → OFFSET 0 LIMIT 1000     (30초)
  페이지 2: GROUP BY 3,000만 행 → 정렬 → OFFSET 1000 LIMIT 1000  (30초)
  ...
  페이지 1000: GROUP BY 3,000만 행 → 정렬 → OFFSET 999000 LIMIT 1000 (30초+)
  총 집계 실행: 1,000회 → 8시간 이상
```

### 대규모 이커머스 기준 비교

| 관점 | CursorReader | PagingReader |
|------|-------------|-------------|
| **GROUP BY 집계 쿼리** | ✅ 1회 실행 후 결과 스트리밍 | ❌ 페이지마다 집계 재실행. 대규모에서 치명적 |
| **커넥션 점유** | ❌ Step 전체 동안 1개 점유 | ✅ 페이지 조회 시만 점유, 사이에 반환 |
| **OFFSET 성능** | 해당 없음 | ❌ 뒤쪽 페이지일수록 스캔량 증가 |
| **데이터 변경 안전성** | ✅ 쿼리 시점 스냅샷 (커서 유지) | ❌ 페이지 간 데이터 변경 시 누락/중복 |
| **멀티스레드** | ❌ ResultSet 공유 상태 → 데이터 오염 | ✅ 각 스레드가 독립 쿼리 실행 |
| **재시작** | ⚠️ read count 기반 (제한적) | ✅ 페이지 번호 자동 저장 |

### CursorReader가 멀티스레드에서 불가능한 이유

CursorReader는 하나의 DB 커넥션에서 **하나의 ResultSet을 열어두고 `next()`로 한 행씩 이동**한다. ResultSet은 "지금 커서가 가리키는 행"이라는 상태를 가지고 있다:

```
Thread A: reader.read() → resultSet.next() → row 3 반환
Thread B: reader.read() → resultSet.next() → row 4 반환  ← 동시 호출

→ 커서가 2칸 전진하여 row 누락
→ 또는 Thread A가 읽으려던 행을 Thread B가 밀어버림 (데이터 오염)
```

PagingReader는 페이지마다 **별도 쿼리를 별도 커넥션으로 실행**하므로 공유 상태가 없어 안전하다.

### 커넥션 점유 문제의 해법

CursorReader의 커넥션 점유가 문제가 되는 것은 **여러 Job이 동시에 실행되어 커넥션 풀이 고갈**될 때다. 이것을 해결하기 위해 PagingReader로 전환하면 GROUP BY 반복 실행이라는 더 큰 문제가 생긴다.

**정석적 해법은 배치 전용 DataSource(Replica) 분리다.** 배치가 Replica에서 읽으면 서비스 DB의 커넥션 풀과 독립되므로, CursorReader의 커넥션 점유가 서비스에 영향을 주지 않는다. 분석한 배치 프로젝트 2개도 RODB/RWDB를 5~6쌍으로 분리하여 이 문제를 해결하고 있었다.

### 병렬화가 필요해지면: Partitioning

상품이 수백만 건으로 늘어나 병렬 처리가 필요해지면, PagingReader로 전환하는 대신 **Partitioning**이 적합하다:

```
Master Step: product_id 범위를 파티션으로 분할
  ├── Partition 1: product_id 1~100,000     → CursorReader (독립 커넥션)
  ├── Partition 2: product_id 100,001~200,000 → CursorReader (독립 커넥션)
  ├── Partition 3: product_id 200,001~300,000 → CursorReader (독립 커넥션)
  └── ...

각 파티션이 독립 커넥션 + 독립 CursorReader → GROUP BY 1회 + 병렬 처리
```

CursorReader의 장점(1회 쿼리)을 유지하면서 병렬화를 달성한다.

### 우리 설계에서의 결론

| 판단 | 근거 |
|------|------|
| **CursorReader 선택** | GROUP BY 집계 쿼리에서 PagingReader는 페이지마다 집계를 재실행하므로 부적합 |
| **현재 단일 스레드** | 결과 100건, 수초 완료. 멀티스레드 불필요 |
| **병렬화 시 전환 경로** | PagingReader가 아닌 Partitioning으로 전환. CursorReader 유지 가능 |
| **커넥션 점유 대응** | 현재 단일 Job 실행이므로 문제없음. 다중 Job 동시 실행 시 Replica DataSource 분리 |

---

## 소재 9: (구현 후 추가 예정)

- 멱등성을 DELETE+INSERT로 보장하는 패턴
- Spring Batch 파라미터 설계와 Job Instance 동일성
- MV vs Redis 실제 랭킹 비교 결과 (score 차이 분석)
- 대량 데이터 성능 테스트 결과
