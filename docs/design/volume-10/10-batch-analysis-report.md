# 10. 배치 어플리케이션 분석 보고서

> 배치 앱 2개(production 브랜치)를 분석하고, Spring Batch 주간/월간 랭킹 MV 적재에 적용할 인사이트를 추출한 보고서.

---

## 분석 대상

| 배치 앱 | 도메인 | Job 수 | 핵심 역할 |
|---------|--------|--------|----------|
| **aurora-x2bee-batch-gddp** (배치 A) | 상품/전시/검색 | 49개 | 상품 리뷰 집계, 검색 인덱스 적재, 베스트/신상품 산정, SAP 연동 |
| **aurora-x2bee-batch-mbod** (배치 B) | 주문/회원/정산 | 48개 | 마일리지 소멸, 회원 등급 변경, 매출/재고 통계, PG 정산 대사 |

---

## 1. 배치 A — aurora-x2bee-batch-gddp (상품/전시/검색)

### 구조 분석

| 항목 | 내용 |
|------|------|
| **총 Job 수** | 49개 (Tasklet 36 + Chunk 8 + Stub 5) |
| **주요 도메인** | 전시(display), 이벤트(event), 상품(goods), 검색(search), 입점사(vendor) |
| **처리 모델** | **Tasklet 73% / Chunk 16% / Stub 11%** |
| **DB** | PostgreSQL + MySQL, RODB/RWDB 분리 (5쌍) |
| **ORM** | MyBatis 중심 (34개 XML 매퍼) |
| **Spring Boot** | 3.3.4, Java 17 |

### Job 카테고리별 분포

| 카테고리 | Job 수 | 처리 모델 | 대표 Job |
|---------|--------|----------|---------|
| Display | 3 | Tasklet | GoodsBestJob, GoodsNewJob |
| Event | 9 | Tasklet | BatEventState, BatMbrBase |
| Goods | 15 | Tasklet + 일부 Chunk | GoodsReviewTotalJob, GoodsSoldOutJob |
| Search | 13 | Tasklet + Chunk | SearchProductChunkLoad, SearchProductIndex |
| Vendor | 4 | Tasklet | EtEntrEvltDayAgrt, VenderEndContract |
| Sample | 6 | Chunk (학습용) | SampleJdbc, SampleMyBatisCursor |

### Reader 패턴

| Reader | 사용처 | 특징 |
|--------|-------|------|
| **MyBatisCursorItemReader** | 검색 상품 로드, 샘플 | 커서 스트리밍, 메모리 효율적 |
| **MyBatisPagingItemReader** | 검색 인덱스 (pageSize=10,000) | ExecutionContext 저장으로 재시작 가능 |
| **JdbcCursorItemReader** | 샘플 (fetchSize=1,000) | BeanPropertyRowMapper 사용 |
| **JdbcPagingItemReader** | 샘플 (pageSize=1,000) | SqlPagingQueryProviderFactoryBean |
| **FlatFileItemReader** | 샘플 (CSV) | linesToSkip=1, ClassPathResource |

### Writer 패턴

- **CompositeItemWriter**: 다중 Writer를 순차 실행 (UPDATE + INSERT 조합)
- **MyBatisBatchItemWriter**: `assertUpdates(false)` — 영향 행 0건이어도 에러 아님
- **커스텀 Lambda Writer**: 검색 Job에서 REST API 호출 (200건씩 서브 배치)
- **UPSERT**: `INSERT ... ON DUPLICATE KEY UPDATE` (GoodsReviewTotal 등 집계 Job)

### SQL 특징

- **GROUP BY + SUM/COUNT/AVG** 집계를 Reader SQL에서 처리
- **LEFT JOIN LATERAL**: 상관 서브쿼리로 복잡한 조인
- **동적 조건 분기**: `batchTyp` 파라미터(A/R/D/M/AFTERDATE)에 따라 WHERE 절 변경
- **시간 기반 필터링**: `DATE_SUB(NOW(), INTERVAL 60 MINUTE)` 등 증분 처리

### 에러 처리

- **SingleJobExecutionListener**: 중복 실행 방지 (같은 Job이 이미 실행 중이면 예외)
- **StepExecutionListener** (검색 인덱스): `beforeStep()`에서 배치 프로세스 카운트 체크, `afterStep()`에서 메타데이터 갱신
- **Skip/Retry 없음**: 실패 시 즉시 종료

### 내 과제 시사점

- **집계 쿼리를 Reader SQL에서 처리하는 패턴**이 핵심 참고 대상. `GROUP BY product_id`로 일간 메트릭을 기간별로 합산하는 것은 Reader SQL에서 처리 가능
- **UPSERT 패턴** (`INSERT ... ON DUPLICATE KEY UPDATE`)이 MV 갱신 대안 중 하나
- **batchTyp 파라미터로 동일 Job에서 주간/월간 분기**하는 방식 — 하나의 Job Config로 scope 파라미터를 받아 처리 가능
- **MyBatisCursorItemReader가 대량 조회의 기본 선택** — 기존 RankingCorrectionJob의 JdbcCursorItemReader와 동일한 전략

---

## 2. 배치 B — aurora-x2bee-batch-mbod (주문/회원/정산)

### 구조 분석

| 항목 | 내용 |
|------|------|
| **총 Job 수** | 48개 (Tasklet 46 + Chunk 2) |
| **주요 도메인** | 정산(adjust), 배송(delivery), 회원(member), 주문(order), **통계(statistics)** |
| **처리 모델** | **Tasklet 96% / Chunk 4%** — 마일리지 소멸, 회원 등급 변경만 Chunk |
| **DB** | MySQL, RODB/RWDB 분리 (6쌍) |
| **ORM** | MyBatis 중심 (68개 XML 매퍼) |
| **Spring Boot** | 3.3.4, Java 17 |

### Chunk-Oriented Job 상세 (2개)

**① mileageRemoveJob (CHUNK_SIZE=1,000)**

```
Reader:  MyBatisCursorItemReader → getExpireMileageList (만료 마일리지 조회)
Processor: MbrAsstResponse → MileageExpireRequestVo (변환)
Writer:  CompositeItemWriter (3개)
         ├── UPDATE: 기존 이력 마감 처리
         ├── INSERT: 소멸 이력 생성
         └── UPDATE: 잔액 합계 갱신
```

**② memberGradeChangeJob (CHUNK_SIZE=100, Multi-Step)**

```
Step 1: memberGradeCalcStep (Tasklet) → 등급 산정 → FAILED 시 종료
Step 2: memberGradeChangeStep (Chunk)
         Reader:  MyBatisCursorItemReader → getMbrGradeChangeList
         Processor: 등급 변경 대상 변환
         Writer:  CompositeItemWriter (3개)
                  ├── UPDATE: 회원 등급 변경
                  ├── UPDATE: 이전 등급 이력 종료
                  └── INSERT: 새 등급 이력 생성
Step 3: memberGradeCouponIssueStep (Tasklet) → 등급 변경 쿠폰 발급
```

### 통계 Job 분석 (10개, 모두 Tasklet)

| Job | 내용 |
|-----|------|
| orderSaleStatisticsJob | 주문 매출 통계 |
| orderSaleStatisticsByGoodsJob | 상품별 매출 통계 |
| orderSaleStatisticsByCouponJob | 쿠폰별 매출 통계 |
| memberOrderStatisticsJob | 회원별 주문 통계 |
| inventoryStatisticsByGoodsJob | 상품별 재고 통계 |
| paymentMethodStatisticsJob | 결제수단별 통계 |
| aggregateBasketJob | 장바구니 집계 |
| dailyGoodsDetailInflowStatisticsJob | 일간 상품 상세 유입 통계 |
| dailyUmamiInflowStatisticsJob | 일간 유입 통계 |
| infDispCtgStatisticsJob | 전시 카테고리 통계 |

> **주목**: 통계/집계 Job이 10개인데 **전부 Tasklet**. 회사에서는 "Tasklet 내부에서 직접 SQL로 집계 → INSERT"하는 패턴을 선호.

### 에러 처리

- **SingleJobExecutionListener**: gddp와 동일 (중복 실행 방지)
- **Multi-Step 조건 분기**: `memberGradeChangeJob`에서 Step 1 실패 시 `.on("FAILED").end()`로 후속 Step 스킵
- **Skip/Retry 없음**

### 내 과제 시사점

- **통계/집계에 Tasklet을 쓰는 이유**: SQL 한 방(GROUP BY + INSERT INTO ... SELECT)으로 처리 가능한 경우 Reader/Processor/Writer 패턴이 오히려 과잉. Chunk는 "행 단위 변환"이 필요할 때만 사용
- **CompositeItemWriter로 다중 테이블 갱신**: MV 적재 시 "기존 데이터 삭제 → 새 데이터 삽입"을 하나의 트랜잭션에서 처리하는 패턴
- **Multi-Step 조건 분기**: Step 1에서 데이터 검증/전처리 → Step 2에서 본 처리 — 내 과제에서 "기존 MV 삭제 Step → 집계 적재 Step"으로 활용 가능
- **UniqueRunIdIncrementer**: `System.currentTimeMillis()`로 run.id 생성 → 같은 파라미터로 재실행 가능 (멱등성과 관련)

---

## 3. 비교 테이블

| 비교 항목 | 배치 A (gddp) | 배치 B (mbod) | **내 과제 (추천)** | **근거** |
|----------|--------------|--------------|-------------------|---------|
| **처리 모델** | Tasklet 73% / Chunk 16% / Stub 11% | Tasklet 96% / Chunk 4% | **Chunk-Oriented + Partitioning** | 대규모 집계 병렬 처리. Tasklet이 효율적인 경우도 있지만, Chunk의 운영 기능(retry, 모니터링) 활용 |
| **Reader 타입** | MyBatisCursorItemReader 주력 | MyBatisCursorItemReader (2건) | **JdbcCursorItemReader** | 기존 RankingCorrectionJob과 일관성 유지. 집계 쿼리가 단순하므로 MyBatis 매퍼 오버헤드 불필요 |
| **비즈니스 로직 위치** | Reader SQL에서 GROUP BY 집계 수행 | Tasklet 내부에서 SQL 직접 실행 | **Reader SQL에서 집계 + Processor에서 score 계산** | GROUP BY는 DB가 효율적, score 공식(log₁₀ 정규화)은 Java 코드가 명확 |
| **Writer 전략** | UPSERT (`ON DUPLICATE KEY UPDATE`) | CompositeItemWriter (UPDATE+INSERT) | **DELETE+INSERT** (기간별 전체 교체) | TOP 100만 저장하므로 UPSERT보다 DELETE+INSERT가 단순. 멱등성 자동 보장 |
| **멱등성 보장** | UniqueRunIdIncrementer (매번 새 실행) | UniqueRunIdIncrementer + batchDate | **DELETE+INSERT로 자연 멱등성** | 같은 기간 데이터를 삭제 후 재적재 → 2회 실행해도 결과 동일 |
| **에러 처리** | SingleJobExecutionListener | SingleJobExecutionListener + Step Flow | **SingleJobExecutionListener + Multi-Step Flow** | Step 1(삭제) 실패 시 Step 2(적재) 미실행으로 데이터 보호 |
| **실행 방식** | REST API 트리거 | REST API 트리거 | **CommandLineRunner 또는 스케줄러** | commerce-batch 모듈의 기존 실행 방식 따르기 |
| **DB 분리** | RODB/RWDB 분리 (5쌍) | RODB/RWDB 분리 (6쌍) | **단일 DataSource** | 현재 규모에서 불필요. 스케일아웃 시 분리 고려 |

---

## 4. 내 과제 설계 제안

### 핵심 인사이트

회사 배치 코드에서 배운 가장 중요한 점:

> **"통계/집계 Job은 대부분 Tasklet으로 SQL 한 방 처리한다."**
> 그러나 과제 요구사항이 Chunk-Oriented 학습이므로, **Reader SQL에서 집계 → Processor에서 score 계산/순위 산정 → Writer에서 MV 적재**하는 구조가 적합하다.

### 설계 질문 답변

**Q1. Reader: JdbcCursorItemReader vs JdbcPagingItemReader**

→ **JdbcCursorItemReader 추천.** 두 회사 앱 모두 CursorItemReader를 주력으로 사용. 집계 쿼리 결과(상품 수 = 수천~수만 행)는 커서로 충분히 처리 가능하고, 정렬 순서 보장도 자연스럽다. PagingReader는 집계 쿼리에서 OFFSET 기반 페이징 시 데이터 누락 위험이 있다.

**Q2. Processor vs SQL**

→ **SQL에서 GROUP BY 집계, Processor에서 score 계산.** gddp의 GoodsReviewTotal이 이 패턴을 사용한다. DB가 잘하는 것(집계)은 DB에, 비즈니스 공식(log₁₀ 정규화 + tiebreaker)은 Java 코드에.

**Q3. Writer 전략: DELETE+INSERT vs UPSERT**

→ **DELETE+INSERT 추천.** TOP 100만 저장하므로 UPSERT로 처리하려면 "이번 주 TOP 100에서 빠진 상품"을 별도로 삭제해야 한다. 기간 키 기준 DELETE 후 INSERT가 단순하고 멱등성도 자동 보장. mbod의 통계 Job들도 이 패턴을 사용한다.

**Q4. 멱등성**

→ **"DELETE WHERE period_key = ? → INSERT" 패턴으로 자연 멱등성.** UniqueRunIdIncrementer로 같은 파라미터로 재실행 허용 + 적재 전 기존 데이터 삭제 → 몇 번을 돌려도 결과 동일.

**Q5. Redis vs MV 공존**

→ **Redis = Speed Layer (실시간 근사치), MV = Batch Layer (DB 원장 기반 정확값).** API에서 scope별로:

- `daily` → Redis ZSET (기존 유지)
- `weekly/monthly` → **MV 우선, Redis fallback** (MV가 DB 원장 기반이므로 정확도 우위. Redis 장애 시에도 조회 가능)

### 구체적 Job 구조 제안

```
WeeklyMonthlyRankingJob
  ├── Parameter: targetDate, scope(weekly/monthly)
  │
  ├── Step 1: cleanupStep (Tasklet)
  │   └── DELETE FROM mv_product_rank_{scope} WHERE period_key = ?
  │
  └── Step 2: aggregateStep (Chunk, chunkSize=1000)
      ├── Reader: JdbcCursorItemReader
      │   └── SELECT product_id, SUM(view_count), SUM(like_count), SUM(order_count)
      │       FROM product_metrics
      │       WHERE metric_date BETWEEN ? AND ?
      │       GROUP BY product_id
      │
      ├── Processor: score 계산 (기존 Score v2 공식 재활용)
      │   └── 0~1 정규화 + log₁₀ + tiebreaker → 순위 산정
      │
      └── Writer: JdbcBatchItemWriter
          └── INSERT INTO mv_product_rank_{scope}
              (product_id, rank, score, view_count, like_count, order_count, period_key)
```

---

## 5. 심층 분석: 통계 Tasklet 내부 SQL 패턴

> mbod의 통계 Job 10개 + 대시보드 Job 1개의 실제 SQL을 분석하여 패턴을 분류했다.

### SQL 패턴 분류

| 패턴 | 해당 Job | 특징 |
|------|---------|------|
| **DELETE + INSERT...SELECT...GROUP BY** | InfDispCtgStatistics, InventoryStatisticsByGoods, MemberOrderStatistics, OrderSaleStatisticsByGoods, PaymentMethodStatistics | 가장 흔한 패턴. 날짜 기준 DELETE 후 SQL 한 방으로 집계+적재 |
| **INSERT...SELECT + ON DUPLICATE KEY UPDATE** | DailyUmamiInflowStatistics, OrderSaleStatistics, DashboardOrderSale | UPSERT 패턴. CTE + UNION ALL로 복잡한 다차원 집계 |
| **SELECT → Java 루프 → foreach INSERT** | AggregateBasket | Java에서 변환 후 벌크 INSERT |
| **SELECT → Java 루프 → foreach MERGE** | DailyGoodsDetailInflowStatistics | Java에서 변환 후 개별 UPSERT |

### 패턴별 상세

**패턴 1: DELETE + INSERT...SELECT (5개 Job, 가장 일반적)**

```sql
-- Step 1: 기간 데이터 삭제
DELETE FROM sm_daycl_inf_ord_agrt WHERE agrt_dt = #{agrtDt}

-- Step 2: 집계 결과 직접 적재
INSERT INTO sm_daycl_inf_ord_agrt (agrt_dt, goods_no, ord_cnt, ...)
SELECT #{agrtDt}, goods_no, COUNT(*), ...
FROM sm_daycl_ord_agrt
WHERE agrt_std_dt = #{agrtDt}
GROUP BY goods_no
```

- **멱등성**: DELETE로 기존 데이터 제거 → INSERT로 재적재. 자연 멱등
- **적합**: 일간/날짜 기준 집계. 내 과제의 MV 갱신에 가장 적합한 패턴
- **특징**: MemberOrderStatistics는 `CASE WHEN`으로 나이대 버킷팅, InventoryStatistics는 2개 CTE로 상품/아이템 재고 결합

**패턴 2: INSERT...SELECT + ON DUPLICATE KEY UPDATE (3개 Job, 가장 복잡)**

```sql
-- OrderSaleStatistics: ~410줄 SQL
WITH DAY_INFO AS (...),
     BNF_INFO AS (...),
     ORD_DTL_INFO AS (...)
INSERT INTO sm_daycl_ord_agrt (agrt_std_dt, entr_no, ord_sales_cnt, ...)
SELECT ...
FROM ORD_DTL_INFO
-- 4개 UNION ALL: 주문접수/주문완료 × 정상/취소
UNION ALL ...
ON DUPLICATE KEY UPDATE
    ord_sales_cnt = VALUES(ord_sales_cnt),
    ...
```

- **멱등성**: PK 충돌 시 UPDATE로 덮어쓰기. 자연 멱등
- **적합**: 다차원 집계 + Late-Arriving Fact 대응 (배송 완료가 주문일 이후 도착)
- **특징**: OrderSaleStatistics가 가장 복잡(410줄). 3개 CTE + 4개 UNION ALL로 주문접수/완료, 정상/취소를 분리 집계

**패턴 3: SELECT → Java → 벌크 INSERT (1개 Job)**

```java
// AggregateBasketServiceImpl
List<SmBasketAgrt> list = mapper.getBasketAgrtList(param);  // CTE + GROUP BY
trxMapper.deleteAll();                                       // 전체 삭제
trxMapper.insertBulkSmBasketAgrt(list);                      // foreach INSERT
```

- **멱등성**: deleteAll → insertBulk. 전체 교체
- **적합**: Java에서 추가 변환이 필요한 경우
- **특징**: 읽기 쿼리에 `ROW_NUMBER() OVER (PARTITION BY)` 윈도우 함수 사용

**패턴 4: SELECT → Java → 개별 MERGE (1개 Job)**

```java
// DailyGoodsDetailInflowStatisticsServiceImpl
List<GoodsInflowAgrt> list = umamiMapper.getDailyGoodsDetailInflowAgreementList(param);
for (GoodsInflowAgrt item : list) {
    trxMapper.mergeSmDayclGoodsInfAgrt(item);  // INSERT...ON DUPLICATE KEY UPDATE
}
```

- **멱등성**: 개별 UPSERT. 자연 멱등
- **적합**: 외부 시스템(Umami) 데이터를 Java로 변환 후 적재
- **비효율**: 행 단위 UPSERT → 대량 데이터에서 성능 저하 (GoodsReviewTotal과 동일한 문제)

### 통계 Job 간 의존 관계

```
OrderSaleStatisticsJob (원천 집계)
  ├── OrderSaleStatisticsByGoodsJob (상품별 재집계)
  ├── PaymentMethodStatisticsJob (결제수단별 재집계)
  └── InfDispCtgStatisticsJob (전시 카테고리별 재집계)
```

> **시사점**: 내 과제에서도 주간/월간 Job이 일간 product_metrics에 의존하므로, 실행 순서 관리가 필요하다.

### 내 과제에 대한 결론

**DELETE + INSERT...SELECT 패턴이 가장 적합.** 이유:

1. 회사 통계 Job 10개 중 5개(50%)가 이 패턴 사용 — 가장 일반적
2. 내 과제의 MV(TOP 100)는 전체 교체가 자연스러움 (순위가 바뀌므로 증분 갱신 불가)
3. 멱등성 자동 보장
4. SQL 복잡도가 낮아 유지보수 용이

다만 과제 요구사항이 **Chunk-Oriented**이므로, SQL 한 방(Tasklet) 대신 Reader에서 GROUP BY 집계 → Processor에서 score 계산 → Writer에서 INSERT 하는 구조로 분해한다.

---

## 6. 심층 분석: UniqueRunIdIncrementer

> 두 앱 모두 동일한 커스텀 구현을 사용한다. Spring Batch 기본 동작과의 차이를 분석했다.

### 구현 코드 (두 앱 동일, production 브랜치)

```java
public class UniqueRunIdIncrementer extends RunIdIncrementer {
    private static final String RUN_ID = "run.id";

    @Override
    public JobParameters getNext(JobParameters parameters) {
        UUID uuid = UUID.randomUUID();
        return new JobParametersBuilder()
                .addString(RUN_ID, uuid + Long.toString(System.currentTimeMillis()))
                .toJobParameters();
    }
}
```

> **이전 브랜치와의 차이**: `addLong(RUN_ID, System.currentTimeMillis())` → `addString(RUN_ID, UUID + timestamp)`. 밀리초 단위 충돌 가능성을 UUID로 해소. `Long` → `String`으로 타입도 변경.

### Spring Batch 기본 RunIdIncrementer와의 비교

| 항목 | 기본 RunIdIncrementer | 커스텀 UniqueRunIdIncrementer |
|------|----------------------|------------------------------|
| **run.id 생성** | 순차 증가 (`run.id + 1`) | `UUID + System.currentTimeMillis()` (UUID + 타임스탬프) |
| **기존 파라미터** | **보존** (기존 파라미터에 run.id만 추가) | **전부 버림** (run.id만 남는 새 JobParameters 생성) |
| **Job Instance 식별** | jobName + 모든 파라미터(run.id 제외) | jobName만으로 식별 (다른 파라미터가 없으므로) |
| **재실행** | 같은 파라미터 + 새 run.id = 같은 Instance의 새 Execution | 매번 새 Execution |
| **유니크 보장** | 순차 → 충돌 없음 | 밀리초 → 동시 실행 시 이론적 충돌 가능 (극히 희소) |

### 핵심 설계 의도

**"같은 Job을 언제든 제한 없이 재실행 가능하게 한다."**

- 기본 RunIdIncrementer는 이전 파라미터를 보존하므로, `targetDate=20260414`로 실행한 Job을 다시 실행하면 **같은 Job Instance에 새 Execution**이 생긴다
- 커스텀 UniqueRunIdIncrementer는 파라미터를 전부 버리므로, **항상 같은 Job Instance**에 매번 새 Execution이 생긴다
- `SingleJobExecutionListener`와 조합하여 "동시 실행만 방지, 순차 재실행은 허용"하는 전략

### 내 과제에 대한 시사점

**주의: 이 패턴은 파라미터 기반 멱등성과 충돌한다.**

내 과제에서 `targetDate`와 `scope`를 JobParameter로 받아야 하는데, UniqueRunIdIncrementer를 그대로 쓰면 **파라미터가 버려진다.** 따라서:

| 선택지 | 장점 | 단점 |
|--------|------|------|
| **기본 RunIdIncrementer 사용** | 파라미터 보존, 같은 날짜 재실행 가능 | Spring Batch가 "이미 완료된 Instance" 에러를 낼 수 있음 |
| **UniqueRunIdIncrementer + 파라미터 직접 추가** | 재실행 자유 | 파라미터가 JobParameters에 포함되지 않아 @Value 주입 불가 |
| **커스텀 Incrementer (파라미터 보존 + 타임스탬프)** | 파라미터 보존 + 재실행 자유 | 구현 필요 |

**추천**: 기본 `RunIdIncrementer`를 사용하되, Writer에서 DELETE+INSERT로 멱등성을 보장하는 것이 가장 단순하다.

---

## 7. 심층 분석: GoodsReviewTotal UPSERT 패턴

> gddp의 상품 리뷰 집계 Job이 사용하는 UPSERT의 실제 SQL과 구조를 분석했다.

### 실행 흐름

```
GoodsReviewTotalJobConfig
  └── GoodsReviewTotalJobTasklet (@StepScope, batchTyp/chngDtm 파라미터)
       └── GoodsReviewTotalServiceImpl.run(batchTyp)
            ├── Step 1: seltGoodsReviewTotalStep1() → 리뷰 집계 SELECT
            ├── Step 2: insertUpdatePrGoodsRevAgrtInfo() → 행 단위 UPSERT 루프
            └── Step 3: syncGoodsSummaryRevCnt() → 전시 요약 테이블 동기화
```

### 집계 SELECT (Reader 역할)

```sql
SELECT
    PGRI.GOODS_NO,
    COUNT(PGRI.REV_NO) AS REV_CNT,
    SUM(hlpful.HLPFUL_CNT) AS HLPFUL_CNT,
    SUM(PGRI.REV_SCR_VAL) AS SUM_SCR_VAL,
    ROUND(AVG(PGRI.REV_SCR_VAL), 1) AS REV_SCR_VAL_AVG_VAL
FROM pr_goods_rev_info PGRI
LEFT JOIN LATERAL (
    SELECT COUNT(REV_NO) AS HLPFUL_CNT
    FROM pr_goods_rev_hlpful_info PGRHI
    WHERE PGRHI.REV_NO = PGRI.REV_NO
) hlpful ON TRUE
WHERE PGRI.REV_DISP_STAT_CD = '20'
  AND PGRI.DEL_YN != 'Y'
  -- batchTyp에 따른 동적 조건:
  -- R(실시간): PGRI.SYS_MOD_DTM BETWEEN DATE_SUB(NOW(), INTERVAL 60 MINUTE) AND NOW()
  -- D(일간):   PGRI.SYS_MOD_DTM >= CURDATE()
  -- M(수동):   PGRI.SYS_MOD_DTM >= #{chngDtm}
  -- ALL:       조건 없음 (전체)
GROUP BY PGRI.GOODS_NO
```

**특징**: LEFT JOIN LATERAL로 리뷰별 도움 수를 상관 서브쿼리로 집계. `batchTyp`에 따라 증분/전체 선택 가능.

### UPSERT SQL (Writer 역할)

```sql
INSERT INTO pr_goods_rev_agrt_info (
    GOODS_NO,
    REV_CNT,
    HLPFUL_CNT,
    REV_STARSCR_AVG_VAL,
    SYS_REG_ID, SYS_REG_DTM,
    SYS_MOD_ID, SYS_MOD_DTM
) VALUES (
    #{goodsNo},
    CAST(#{revCnt} AS SIGNED),
    CAST(#{hlpfulCnt} AS SIGNED),
    CAST(#{revScrValAvgVal} AS DECIMAL(10,2)),
    #{sysRegId}, now(),
    #{sysModId}, now()
)
ON DUPLICATE KEY UPDATE
    REV_CNT = CAST(#{revCnt} AS SIGNED),
    HLPFUL_CNT = CAST(#{hlpfulCnt} AS SIGNED),
    REV_STARSCR_AVG_VAL = CAST(#{revScrValAvgVal} AS DECIMAL(10,2)),
    SYS_MOD_ID = #{sysModId},
    SYS_MOD_DTM = now()
```

**PK**: `GOODS_NO` (상품번호)

### UPSERT vs DELETE+INSERT 트레이드오프 (실무 코드 기반)

| 관점 | UPSERT (GoodsReviewTotal 방식) | DELETE+INSERT (통계 Job 방식) |
|------|-------------------------------|------------------------------|
| **적합한 경우** | 1:1 매핑 (상품 → 집계 1행). 기존 데이터에서 빠지는 행이 없음 | TOP-N 랭킹처럼 기간마다 대상이 바뀌는 경우 |
| **멱등성** | 자연 멱등 (PK 충돌 시 UPDATE) | 자연 멱등 (DELETE 후 재적재) |
| **잔여 데이터** | 이전에 있던 행이 그대로 남음 (삭제 안 됨) | 기간 키 기준 깨끗하게 교체 |
| **성능 (소규모)** | 행 단위 UPSERT → N번 DB 호출 | DELETE 1회 + 벌크 INSERT 1회 |
| **성능 (대규모)** | 행 단위 루프가 병목 | DELETE가 락 범위 넓을 수 있음 |
| **감사 추적** | SYS_REG_DTM(최초) / SYS_MOD_DTM(최종) 분리 가능 | 매번 새 행이므로 최종 적재 시각만 기록 |

### GoodsReviewTotal의 비효율 포인트

1. **행 단위 UPSERT 루프**: `for (item : list) { mapper.insertUpdate(item); }` — 상품 10만 건이면 10만 번 DB 호출
2. **같은 회사의 GoodsSummarySyncJob**은 `INSERT...SELECT...ON DUPLICATE KEY UPDATE`로 SQL 한 방 처리 → 훨씬 효율적
3. Java 루프 UPSERT는 Reader/Processor가 필요한 Chunk에서도 동일한 비효율 발생 가능

### 내 과제에 대한 결론

**DELETE+INSERT가 내 과제에 더 적합한 이유**:

1. **TOP 100 랭킹은 기간마다 대상이 바뀐다** — 이번 주 TOP 100에 있던 상품이 다음 주에는 빠질 수 있음. UPSERT는 빠진 상품을 삭제하지 않으므로 잔여 데이터 문제 발생
2. **기간 키(period_key) 기준 전체 교체**가 의미적으로 깔끔 — "이번 주 랭킹"은 하나의 단위로 교체되어야 함
3. **JdbcBatchItemWriter의 벌크 INSERT**는 행 단위 UPSERT보다 성능 우위
4. GoodsReviewTotal의 행 단위 UPSERT 루프는 **안티패턴** — 내 과제에서 피해야 할 패턴

---

## 8. MV Score 계산 전략: 메트릭 합산 후 score 1회 계산 (방식 A)

> Redis의 주간/월간 랭킹은 "일별 score를 합산/감쇠"하는 근사치 방식이다.
> MV는 DB 원장 기반의 정확한 기간 집계를 제공하기 위한 Batch Layer이므로, 다른 계산 방식을 적용한다.

### Redis vs MV의 score 계산 차이

| 항목 | Redis 주간 | Redis 월간 | MV (방식 A) |
|------|-----------|-----------|------------|
| **입력** | 7개 daily ZSET의 score | 전일 monthly score + 당일 daily score | product_metrics 원시 메트릭 |
| **계산** | `ZUNIONSTORE(SUM)` — 일별 score 단순 합산 | `전일 × 0.97 + 당일 × 1.0` — 지수 감쇠 롤링 | `SUM(메트릭) → score 공식 1회 적용` |
| **특성** | log₁₀ 비선형성으로 인한 왜곡 가능 | carry-over 누적 근사치 | DB 원장 기반 정확값 |
| **의미** | "일별 인기도의 합" | "최근 활동에 가중치를 둔 인기도" | "기간 총 활동량 기반 인기도" |

### 왜 방식 A인가: log₁₀ 비선형성 문제

Redis 주간 방식(일별 score 합산)은 수학적으로 부정확할 수 있다:

```
예시: 상품 X — 7일간 view_count = [100, 100, 100, 100, 100, 100, 100]
예시: 상품 Y — 7일간 view_count = [0, 0, 0, 0, 0, 0, 700]

Redis 주간 (일별 score 합산):
  X: 7 × log₁₀(101)/7 = 7 × 0.2862 = 2.0034
  Y: 6 × log₁₀(1)/7 + log₁₀(701)/7 = 0 + 0.4063 = 0.4063
  → X가 압도적 우위 (일별 score 합산이므로 매일 꾸준한 상품이 유리)

MV 방식 A (메트릭 합산 후 score 1회 계산):
  X: log₁₀(700 + 1)/7 = 0.4063
  Y: log₁₀(700 + 1)/7 = 0.4063
  → 동일 (총 활동량이 같으므로 동점)
```

- **Redis 방식**: "꾸준히 인기 있는 상품"을 우대 — 실시간 트렌드 반영에 적합
- **MV 방식 A**: "기간 총 활동량"을 공정하게 평가 — 정확한 기간 집계에 적합

**두 방식은 관점이 다르고, MV의 존재 이유(정확한 Batch Layer)에는 방식 A가 부합한다.**

### Reader SQL 설계

```sql
SELECT
    pm.product_id,
    SUM(pm.view_count) AS total_view_count,
    SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
    SUM(pm.sales_count) AS total_sales_count,
    SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount
FROM product_metrics pm
JOIN product p ON pm.product_id = p.id
WHERE pm.metric_date BETWEEN :startDate AND :endDate
  AND p.deleted_at IS NULL
GROUP BY pm.product_id
```

- **주간**: `startDate = targetDate - 6`, `endDate = targetDate` (7일)
- **월간**: `startDate = targetDate - 29`, `endDate = targetDate` (30일)
- Additive Measure 원칙 준수: 취소는 별도 컬럼이므로 조회 시 차감 (`sales_amount - cancel_amount`)

### Processor 설계

기존 Score v2 공식을 그대로 적용:

```
score = categoryPriority
      + 0.1 × log₁₀(totalViewCount + 1) / 7.0
      + 0.2 × log₁₀(totalNetLikeCount + 1) / 7.0
      + 0.7 × log₁₀(totalNetSalesAmount + 1) / 7.0
      + epochSeconds × 1e-16  (tiebreaker)
```

- **가중치/MAX_LOG**: RankingCorrectionJobConfig의 상수 재활용
- **tiebreaker**: 배치 실행 시점의 `Instant.now().getEpochSecond()` 사용 (동점 해소 용도)
- **TOP-N 필터링**: Processor에서 하지 않음 — 전체 결과를 Writer에 전달하고, Reader SQL에 `ORDER BY score DESC LIMIT 100`을 추가하거나 Writer 후 별도 정리

### Writer 설계 (DELETE + INSERT)

```
Step 1 (Tasklet): DELETE FROM mv_product_rank_{scope} WHERE period_key = :periodKey
Step 2 (Chunk Writer):
    INSERT INTO mv_product_rank_{scope}
    (product_id, ranking, score, view_count, like_count, sales_count, sales_amount, period_key, created_at)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())
```

- **period_key**: 주간 = `2026-W16`, 월간 = `2026-04` (ISO 기반)
- **ranking**: Processor 또는 Writer 단계에서 score 내림차순 순번 부여
- **TOP 100 제한**: Reader SQL에서 `LIMIT 100` 또는 Processor에서 필터링

### 전체 데이터 흐름

```
[product_metrics (DB 원장)]
  │
  │ Reader: GROUP BY product_id, SUM(7일 or 30일)
  ▼
[상품별 기간 메트릭 합계]
  │
  │ Processor: Score v2 공식 적용 (log₁₀ 정규화 + tiebreaker)
  ▼
[상품별 score]
  │
  │ Writer: DELETE period_key → INSERT TOP 100
  ▼
[mv_product_rank_weekly / mv_product_rank_monthly]
  │
  │ API: SELECT WHERE period_key = ? ORDER BY ranking
  ▼
[클라이언트 응답]
```

### Redis와 MV의 역할 분담 (최종 — 단일 소스 원칙)

| 관점 | Redis ZSET | MV 테이블 |
|------|-----------|----------|
| **역할** | Speed Layer — 실시간 근사치 | Batch Layer — DB 원장 기반 정확값 |
| **daily** | 단일 소스 | 불필요 (Redis로 충분) |
| **weekly** | 사용 안 함 (MV 도입 후 제거) | **단일 소스** — 정확한 기간 집계 |
| **monthly** | 사용 안 함 (MV 도입 후 제거) | **단일 소스** — 정확한 기간 집계 |
| **장애 시** | Redis 다운 → daily 조회 불가 | 당일 MV 없으면 → 전일 MV fallback (같은 공식, 1일 stale) |

---

## 부록: 공통 아키텍처 패턴

### 두 앱의 공통 패턴

| 패턴 | 설명 | 내 과제 적용 |
|------|------|------------|
| **UniqueRunIdIncrementer** | `UUID + System.currentTimeMillis()` 기반 run.id → 같은 파라미터로 재실행 가능. 파라미터 전부 버림 | 파라미터 보존이 필요하므로 기본 RunIdIncrementer 사용 |
| **RODB/RWDB 분리** | 읽기는 Replica, 쓰기는 Primary | 현재 규모에서는 단일 DataSource로 충분 |
| **SingleJobExecutionListener** | `JobExplorer.findRunningJobExecutions()`로 중복 실행 방지 | 동일 패턴 적용 가능 |
| **MyBatis + XML Mapper** | SQL을 XML로 외부 관리, 동적 조건 분기 | JdbcCursorItemReader + 인라인 SQL로 충분 |
| **REST API 트리거** | `/jobs/{jobName}?param=value` → JobLauncher.run() | commerce-batch의 기존 실행 방식 따르기 |
| **@JobScope / @StepScope** | JobParameter 주입을 위한 지연 생성 | 필수 적용 (targetDate, scope 파라미터) |
| **assertUpdates(false)** | Writer에서 영향 행 0건 허용 | ETL 시나리오에서 유용 |

### Tasklet vs Chunk 선택 기준 (회사 코드에서 도출)

| 기준 | Tasklet 선택 | Chunk 선택 |
|------|-------------|-----------|
| SQL 복잡도 | INSERT INTO ... SELECT (SQL 한 방) | 행 단위 변환/필터링 필요 |
| 데이터 규모 | SQL이 감당 가능한 범위 | OOM 위험 → chunk 단위 커밋 |
| 비즈니스 로직 | 단순 이동/삭제/갱신 | score 계산, 등급 산정 등 Java 로직 |
| 트랜잭션 | 전체 or nothing | 부분 커밋 필요 (실패 시 일부 복구) |
| 배치 앱 비율 | **85%** (82/97개 Job) | **15%** (10/97개 Job, stub 5개 제외) |
