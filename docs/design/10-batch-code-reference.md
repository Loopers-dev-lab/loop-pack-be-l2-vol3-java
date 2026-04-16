# 10. 회사 배치 코드 참고 스니펫

> 회사 실무 배치 앱(gddp, mbod)에서 추출한 핵심 코드 패턴.
> 구현 시 직접 참고할 수 있도록 패턴별로 분류했다.

---

## 1. 공통 인프라 코드

### UniqueRunIdIncrementer (두 앱 동일)

```java
public class UniqueRunIdIncrementer extends RunIdIncrementer {
    private static final String RUN_ID = "run.id";

    @Override
    public JobParameters getNext(JobParameters parameters) {
        return new JobParametersBuilder()
                .addLong(RUN_ID, System.currentTimeMillis())
                .toJobParameters();
    }
}
```

- **주의**: 기존 파라미터를 전부 버린다. 내 과제에서는 `targetDate`, `scope` 파라미터가 필요하므로 기본 `RunIdIncrementer`를 사용할 것

### SingleJobExecutionListener (중복 실행 방지)

```java
@Component
@Slf4j
public class SingleJobExecutionListener implements JobExecutionListener {

    @Autowired
    private JobExplorer jobExplorer;

    @Override
    public void beforeJob(JobExecution jobExecution) {
        int runningJobsCount = jobExplorer
            .findRunningJobExecutions(jobExecution.getJobInstance().getJobName())
            .size();
        if (runningJobsCount > 1) {
            throw new CommonException(
                "이미 실행 중인 Job이 있습니다. 현재 실행을 중지합니다: "
                + jobExecution.getJobInstance().getJobName());
        }
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        log.debug("End of job: [{}] {}",
            jobExecution.getJobInstance().getInstanceId(),
            jobExecution.getJobInstance().getJobName());
    }
}
```

- `JobExplorer.findRunningJobExecutions()`으로 같은 이름의 실행 중인 Job이 있는지 체크
- 1개 초과 시 예외를 던져 중복 실행 방지

---

## 2. Chunk-Oriented Job 패턴

### 패턴 A: JdbcCursorItemReader + CompositeItemWriter (gddp/SampleJdbcConfig)

```java
@Configuration
@RequiredArgsConstructor
public class SampleJdbcConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;

    @Resource(name = "displayRodbSqlSessionFactory")
    private final SqlSessionFactory displayRodbSqlSessionFactory;

    @Resource(name = "displayRwdbSqlSessionFactory")
    private final SqlSessionFactory displayRwdbSqlSessionFactory;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job sampleJdbcJob() {
        return new JobBuilder("sampleJdbcJob", jobRepository)
                .start(sampleJdbcStep())
                .incrementer(new UniqueRunIdIncrementer())
                .build();
    }

    @Bean
    public Step sampleJdbcStep() {
        return new StepBuilder("sampleJdbcStep", jobRepository)
                .<SampleRequest, SampleRequest>chunk(CHUNK_SIZE, transactionManager)
                .reader(sampleJdbcReader())
                .writer(sampleJdbcWriter())
                .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<SampleRequest> sampleJdbcReader() {
        DataSource displayRodbDataSource =
            (DataSource) ApplicationContextWrapper.getBean("displayRodbDataSource");

        HashMap<String, Object> queryMap = new HashMap<>();
        queryMap.put("name", "James");
        queryMap.put("sysRegrId", "SYSTEM");

        BoundSql boundSql = displayRodbSqlSessionFactory.getConfiguration()
            .getMappedStatement("selectSampleJdbcList").getBoundSql(queryMap);

        return new JdbcCursorItemReaderBuilder<SampleRequest>()
                .name("jdbcCursorItemReader")
                .dataSource(displayRodbDataSource)
                .sql(boundSql.getSql())
                .rowMapper(new BeanPropertyRowMapper<>(SampleRequest.class))
                .preparedStatementSetter(
                    new ArgumentPreparedStatementSetter(getQueryValues(boundSql)))
                .fetchSize(1000)
                .maxItemCount(1000)
                .maxRows(1000)
                .build();
    }

    @Bean
    public ItemWriter<SampleRequest> sampleJdbcWriter() {
        CompositeItemWriter<SampleRequest> compositeItemWriter = new CompositeItemWriter<>();
        compositeItemWriter.setDelegates(Arrays.asList(jdbcBatchItemWriter()));
        return compositeItemWriter;
    }

    @Bean
    public JdbcBatchItemWriter<SampleRequest> jdbcBatchItemWriter() {
        DataSource displayRwdbDataSource =
            (DataSource) ApplicationContextWrapper.getBean("displayRwdbDataSource");

        BoundSql boundSql = displayRwdbSqlSessionFactory.getConfiguration()
            .getMappedStatement("updateSample2").getBoundSql(new SampleRequest());

        return new JdbcBatchItemWriterBuilder<SampleRequest>()
                .dataSource(displayRwdbDataSource)
                .assertUpdates(true)
                .sql(boundSql.getSql())
                .itemPreparedStatementSetter((item, ps) -> {
                    ps.setString(1, "SYSTEM");
                    ps.setString(2, item.getName());
                })
                .build();
    }
}
```

**참고 포인트**:
- `JdbcCursorItemReaderBuilder`의 `.fetchSize()`, `.maxItemCount()`, `.maxRows()` 설정
- `BeanPropertyRowMapper`로 ResultSet → DTO 매핑
- `JdbcBatchItemWriterBuilder`의 `.assertUpdates(true)` — 영향 행이 0이면 에러
- `CompositeItemWriter`로 다중 Writer 체이닝

---

### 패턴 B: MyBatisCursorItemReader + MyBatisBatchItemWriter (gddp/SampleMyBatisCursorJobConfig)

```java
@Configuration
@RequiredArgsConstructor
public class SampleMyBatisCursorJobConfig {

    @Resource(name = "displayRodbSqlSessionFactory")
    private final SqlSessionFactory displayRodbSqlSessionFactory;

    @Resource(name = "displayRwdbSqlSessionFactory")
    private final SqlSessionFactory displayRwdbSqlSessionFactory;

    private static final int CHUNK_SIZE = 1000;

    @Bean
    public Job sampleMyBatisCursorJob() {
        return new JobBuilder("sampleMyBatisCursorJob", jobRepository)
                .start(sampleMyBatisCursorStep())
                .incrementer(new UniqueRunIdIncrementer())
                .build();
    }

    @Bean
    public Step sampleMyBatisCursorStep() {
        return new StepBuilder("sampleMyBatisCursorStep", jobRepository)
                .<SampleRequest, SampleRequest>chunk(CHUNK_SIZE, transactionManager)
                .reader(sampleMyBatisCursorItemReader())
                .writer(sampleeCompositeWriter())
                .build();
    }

    @Bean
    public MyBatisCursorItemReader<SampleRequest> sampleMyBatisCursorItemReader() {
        Map<String, Object> parameterValues = new HashMap<>();
        return new MyBatisCursorItemReaderBuilder<SampleRequest>()
                .sqlSessionFactory(displayRodbSqlSessionFactory)
                .queryId("com.x2bee.batch.gddp.app.repository.displayrodb.sample.BatSampleMapper.selectSampleList")
                .parameterValues(parameterValues)
                .build();
    }

    @Bean
    public ItemWriter<SampleRequest> sampleMyBatisBatchItemWriter() {
        return new MyBatisBatchItemWriterBuilder<SampleRequest>()
                .sqlSessionFactory(displayRwdbSqlSessionFactory)
                .assertUpdates(false)  // 영향 행 0건 허용
                .itemToParameterConverter(item -> {
                    Map<String, Object> parameter = new HashMap<>();
                    parameter.put("sysModrId", "BATCH");
                    parameter.put("name", item.getName());
                    return parameter;
                })
                .statementId("com.x2bee.batch.gddp.app.repository.displayrwdb.sample.BatSampleTrxMapper.updateSample")
                .build();
    }
}
```

**참고 포인트**:
- `MyBatisCursorItemReaderBuilder`는 `queryId`로 매퍼 XML의 SQL을 참조
- `MyBatisBatchItemWriterBuilder`의 `.itemToParameterConverter()`로 DTO → 파라미터 맵 변환
- `.assertUpdates(false)` — ETL에서 "영향 없는 행"이 정상인 경우

---

### 패턴 C: Reader/Processor/Writer 분리 + CompositeItemWriter (gddp/SampleCompositeWriterJobConfig)

```java
@Bean
public Step sampleCompositeWriterStep() {
    return new StepBuilder("sampleCompositeWriterStep", jobRepository)
            .<SampleRequest, SampleResponse>chunk(CHUNK_SIZE, transactionManager)
            .reader(sampleReader())
            .processor(sampleProcessor())      // 타입 변환: Request → Response
            .writer(sampleeCompositeWriter())
            .build();
}

@Bean
public ItemProcessor<SampleRequest, SampleResponse> sampleProcessor() {
    return batSampleCompositeService::processor;  // 메서드 레퍼런스
}

@Bean
public ItemWriter<SampleResponse> sampleeCompositeWriter() {
    CompositeItemWriter<SampleResponse> compositeItemWriter = new CompositeItemWriter<>();
    compositeItemWriter.setDelegates(Arrays.asList(updateWriter()));
    return compositeItemWriter;
}

@Bean
public ItemWriter<SampleResponse> updateWriter() {
    return sampleList -> sampleList.forEach(batSampleCompositeService::writer);
}
```

**참고 포인트**:
- Processor에서 타입 변환 (`SampleRequest` → `SampleResponse`)
- Lambda Writer (`sampleList -> sampleList.forEach(...)`)로 커스텀 로직 실행

---

## 3. Multi-Step + 조건 분기 패턴

### memberGradeChangeJob (mbod) — Step 1 실패 시 후속 Step 스킵

```java
@Bean
public Job memberGradeChangeJob() {
    return new JobBuilder("memberGradeChangeJob", jobRepository)
            .start(memberGradeCalcStep()).on("FAILED").end()           // Step 1 실패 → 종료
            .on("*").to(memberGradeChangeStep("")).on("FAILED").end()  // Step 2 실패 → 종료
            .on("*").to(memberGradeCouponIssueStep())                  // Step 3
            .end()
            .incrementer(new UniqueRunIdIncrementer())
            .listener(singleJobExecutionListener)
            .build();
}

// Step 1: Tasklet (등급 산정)
@Bean
@JobScope
public Step memberGradeCalcStep() {
    return new StepBuilder("memberGradeCalcStep", jobRepository)
            .tasklet(memberGradeCalcTasklet(null), transactionManager)
            .build();
}

// Step 2: Chunk (등급 변경 — Reader/Processor/Writer)
@Bean
@JobScope
public Step memberGradeChangeStep(@Value("#{jobParameters[mbrNo]}") String mbrNo) {
    this.mbrNo = mbrNo;
    this.batchDate = DateUtil.today(X2Constants.YYYYMMDD);
    return new StepBuilder("memberGradeChangeStep", jobRepository)
            .<MemberGradeChangeResponse, MemberGradeChangeRequest>chunk(CHUNK_SIZE, transactionManager)
            .reader(memberGradeChangeItemReader())
            .processor(memberGradeChangeItemProcessor())
            .writer(memberGradeChangeItemWriter())
            .build();
}

// Step 3: Tasklet (쿠폰 발급)
@Bean
@JobScope
public Step memberGradeCouponIssueStep() {
    return new StepBuilder("memberGradeCouponIssueStep", jobRepository)
            .tasklet(memberGradeCouponIssueTasklet(null), transactionManager)
            .build();
}
```

**참고 포인트**:
- `.on("FAILED").end()` — 실패 시 후속 Step 실행하지 않고 종료
- `.on("*").to(nextStep)` — 그 외 모든 상태에서 다음 Step으로
- **내 과제 적용**: `cleanupStep(DELETE).on("FAILED").end() → aggregateStep(Chunk)`

### Processor에서 DTO 변환 (memberGradeChange)

```java
@Bean
public ItemProcessor<MemberGradeChangeResponse, MemberGradeChangeRequest> memberGradeChangeItemProcessor() {
    return item -> {
        MemberGradeChangeRequest request = new MemberGradeChangeRequest();
        request.setSysRegId(Constants.SYS_REG_ID);
        request.setSysModId(Constants.SYS_MOD_ID);
        request.setBatchDate(batchDate);
        request.setMbrNo(item.getMbrNo());
        request.setMbrGradeCd(item.getMbrGradeCd());
        return request;
    };
}
```

### CompositeItemWriter 3개 체이닝 (memberGradeChange)

```java
@Bean
public ItemWriter<MemberGradeChangeRequest> memberGradeChangeItemWriter() {
    CompositeItemWriter<MemberGradeChangeRequest> compositeItemWriter = new CompositeItemWriter<>();
    compositeItemWriter.setDelegates(Arrays.asList(
        memberGradeChangeItemWriter1(),  // UPDATE: 회원 등급 변경
        memberGradeChangeItemWriter2(),  // UPDATE: 이전 등급 이력 종료
        memberGradeChangeItemWriter3()   // INSERT: 새 등급 이력 생성
    ));
    return compositeItemWriter;
}

@Bean
public ItemWriter<MemberGradeChangeRequest> memberGradeChangeItemWriter1() {
    return new MyBatisBatchItemWriterBuilder<MemberGradeChangeRequest>()
            .sqlSessionFactory(orderRwdbSqlSessionFactory)
            .assertUpdates(false)
            .statementId("...EtMbrBaseTrxMapper.modifyEtMbrBaseGradeChange")
            .build();
}
```

---

## 4. Tasklet 패턴

### 단순 Tasklet (통계 Job — mbod)

```java
@RequiredArgsConstructor
@StepScope
@Component
public class OrderSaleStatisticsTasklet implements Tasklet {
    private final OrderSaleStatisticsService orderSaleStatisticsService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        orderSaleStatisticsService.orderSaleStatisticsDataProcess();
        return RepeatStatus.FINISHED;
    }
}
```

### 파라미터 주입 Tasklet (gddp/GoodsReviewTotal)

```java
@Component
@Slf4j
@StepScope
public class GoodsReviewTotalJobTasklet implements Tasklet {

    @Value("#{jobParameters[batchTyp]}")
    private String batchTyp;

    @Value("#{jobParameters[chngDtm]}")
    private String chngDtm;

    @Autowired
    private GoodsReviewTotalService goodsReviewTotalService;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        goodsReviewTotalService.run(batchTyp);
        return RepeatStatus.FINISHED;
    }
}
```

- `@StepScope` + `@Value("#{jobParameters[...]}") `로 파라미터 주입
- Service에 위임하여 실제 로직 처리

---

## 5. StepExecutionListener 패턴 (gddp/SearchProductIndex)

```java
private record IndexBatchCheckListener(
        SearchMapper searchMapper,
        SearchTrxMapper searchTrxMapper
) implements StepExecutionListener {

    @Override
    public void beforeStep(StepExecution stepExecution) {
        int startCount = searchMapper.getSearchIndexLoadBatchProcessCount();
        if (startCount > 0) {
            stepExecution.setTerminateOnly();  // 다른 배치가 실행 중이면 Step 종료
        }
    }

    @Override
    public ExitStatus afterStep(StepExecution stepExecution) {
        if (stepExecution.getExitStatus().equals(ExitStatus.COMPLETED)) {
            searchTrxMapper.updateSearchProductIndexSendYn();  // 메타데이터 갱신
        } else {
            return new ExitStatus(
                ExitStatus.EXECUTING.getExitCode(),
                "Running SearchProductIndexLoadBatch or System Error!"
            );
        }
        return stepExecution.getExitStatus();
    }
}
```

Step에 Listener 등록:

```java
@Bean
@JobScope
public Step searchProductIndexStep() {
    return new StepBuilder("searchProductIndexStep", jobRepository)
            .<SearchProductIndexInfo, SearchProductIndexInfo>chunk(DEFAULT_CHUNK_SIZE, transactionManager)
            .reader(searchProductIndexReader(null, null, null))
            .writer(searchProductIndexWriter())
            .listener(new IndexBatchCheckListener(searchMapper, searchTrxMapper))
            .build();
}
```

**참고 포인트**:
- `record`로 간결하게 구현
- `stepExecution.setTerminateOnly()` — Step 실행 자체를 방지
- `afterStep()`에서 성공 시 후처리 (메타데이터 갱신)

---

## 6. 통계 SQL 패턴

### 패턴 1: DELETE + INSERT...SELECT (가장 일반적, 5개 Job)

```sql
-- SmDayclGoodsOrdAgrtTrxMapper.xml
-- Step 1: 기간 데이터 삭제
DELETE FROM SM_DAYCL_GOODS_ORD_AGRT WHERE AGRT_DT = #{agrtDt}

-- Step 2: 집계 결과 직접 적재
INSERT INTO SM_DAYCL_GOODS_ORD_AGRT (
    AGRT_DT, GOODS_NO, ITM_NO,
    ORD_QTY, ORD_AMT, CNCL_QTY, CNCL_AMT, ...
)
WITH DAY_INFO AS (
    SELECT #{agrtDt} AS AGRT_DT
)
SELECT
    DI.AGRT_DT,
    SDOA.GOODS_NO,
    SDOA.ITM_NO,
    SUM(SDOA.ORD_QTY),
    SUM(SDOA.ORD_AMT),
    SUM(SDOA.CNCL_QTY),
    SUM(SDOA.CNCL_AMT),
    ...
FROM SM_DAYCL_ORD_AGRT SDOA
CROSS JOIN DAY_INFO DI
WHERE SDOA.AGRT_DT = DI.AGRT_DT
GROUP BY SDOA.GOODS_NO, SDOA.ITM_NO
```

**특징**:
- 멱등성 자동 보장 (DELETE 후 재적재)
- SQL 한 방으로 집계+적재 — Tasklet에서 실행
- Additive Measure 원칙: `ORD_QTY`와 `CNCL_QTY` 분리 저장

### 패턴 2: INSERT...SELECT + ON DUPLICATE KEY UPDATE (가장 복잡, 3개 Job)

```sql
-- SmDayclOrdAgrtTrxMapper.xml (OrderSaleStatistics, ~410줄)
WITH DAY_INFO AS (
    SELECT CASE WHEN ... END AS AGRT_STD_DT,
           CASE WHEN ... END AS AGRT_DT
),
BNF_INFO AS (
    SELECT ORD_NO, ORD_SEQ, SUM(BNF_AMT) AS TOT_BNF_AMT
    FROM SM_ORD_BNF_RELS
    GROUP BY ORD_NO, ORD_SEQ
),
ORD_DTL_INFO AS (
    SELECT ... FROM SM_ORD_DTL_INFO
    JOIN product, member, MD info
    WHERE order_date BETWEEN ...
)
INSERT INTO SM_DAYCL_ORD_AGRT (
    AGRT_STD_DT, AGRT_DT, AGRT_GB, ORD_NO, ORD_SEQ, ORD_PROC_SEQ,
    GOODS_NO, ITM_NO, ORD_QTY, ORD_AMT, ...
)
-- 4개 UNION ALL: 주문접수/주문완료 × 정상/취소
SELECT ... FROM ORD_DTL_INFO WHERE ORD_PROC_STAT_CD = '10' -- 주문접수
UNION ALL
SELECT ... FROM ORD_DTL_INFO WHERE ORD_PROC_STAT_CD = '30' -- 주문완료
UNION ALL
SELECT ... FROM ORD_DTL_INFO WHERE CNCL conditions                -- 취소
UNION ALL
SELECT ... FROM ORD_DTL_INFO WHERE CNCL + 완료 conditions         -- 취소+완료
ON DUPLICATE KEY UPDATE
    AGRT_DT = VALUES(AGRT_DT),
    ORD_QTY = VALUES(ORD_QTY),
    ORD_AMT = VALUES(ORD_AMT),
    ...
```

**특징**:
- 3개 CTE + 4개 UNION ALL로 다차원 집계
- Late-Arriving Fact 대응: 주문접수일(AGRT_STD_DT) vs 실제발생일(AGRT_DT) 이중 기록
- PK 충돌 시 UPDATE — 증분 갱신에 적합
- `ORD_QTY`와 `CNCL_QTY` 분리 (Additive Measure)

### 패턴 3: SELECT → Java 루프 → 벌크 INSERT (AggregateBasket)

```java
// Java
List<SmBasketAgrt> list = mapper.getBasketAgrtList(param);  // CTE + GROUP BY + ROW_NUMBER()
trxMapper.deleteAll();                                       // 전체 삭제
trxMapper.insertBulkSmBasketAgrt(list);                      // foreach INSERT
```

---

## 7. UPSERT SQL 실물 (gddp/GoodsReviewTotal)

### 집계 SELECT (Reader)

```xml
<select id="seltGoodsReviewTotalStep1" parameterType="hashmap"
        resultType="PrGoodsRevAgrtInfoResponse">
    SELECT
        GOODS_NO,
        COUNT(REV_NO) AS REV_CNT,
        COALESCE(SUM(HLPFUL_CNT), 0) AS HLPFUL_CNT,
        SUM(REV_SCR_VAL) AS SUM_SCR_VAL,
        ROUND(SUM(REV_SCR_VAL) / COUNT(REV_NO), 1) AS REV_SCR_VAL_AVG_VAL
    FROM (
        SELECT GOODS_NO, REV_NO, PGRHI.HLPFUL_CNT, REV_SCR_VAL
        FROM pr_goods_rev_info PGRI
        LEFT JOIN LATERAL (
            SELECT HLPFUL_CNT FROM (
                SELECT REV_NO, COALESCE(COUNT(REV_NO), 0) AS HLPFUL_CNT
                FROM pr_goods_rev_hlpful_info PGRHI
                GROUP BY REV_NO
            ) PGRHI
            WHERE PGRHI.REV_NO = PGRI.REV_NO
        ) PGRHI ON TRUE
        WHERE PGRI.REV_DISP_STAT_CD = '20'
          AND PGRI.DEL_YN != 'Y'
        <if test='batchTyp!="ALL"'>
            <choose>
                <when test='batchTyp=="R"'>
                    AND PGRI.SYS_MOD_DTM BETWEEN DATE_SUB(NOW(), INTERVAL 60 MINUTE) AND NOW()
                </when>
                <when test='batchTyp=="D"'>
                    AND DATE(PGRI.SYS_MOD_DTM) = CURDATE()
                </when>
                <when test='batchTyp=="M"'>
                    AND DATE(PGRI.SYS_MOD_DTM) = DATE(STR_TO_DATE(#{chngDtm}, '%Y%m%d%H%i%s'))
                </when>
                <when test='batchTyp=="AFTERDATE"'>
                    AND DATE(PGRI.SYS_MOD_DTM) >= DATE(STR_TO_DATE(#{chngDtm}, '%Y%m%d%H%i%s'))
                </when>
                <otherwise>
                    AND FALSE
                </otherwise>
            </choose>
        </if>
    ) PGRI
    GROUP BY PGRI.GOODS_NO
</select>
```

### UPSERT (Writer)

```xml
<insert id="insertUpdatePrGoodsRevAgrtInfo" parameterType="PrGoodsRevAgrtInfoResponse">
    INSERT INTO pr_goods_rev_agrt_info (
        GOODS_NO, REV_CNT, HLPFUL_CNT, REV_STARSCR_AVG_VAL,
        SYS_REG_ID, SYS_REG_DTM, SYS_MOD_ID, SYS_MOD_DTM
    ) VALUES (
        #{goodsNo},
        CAST(#{revCnt} AS SIGNED),
        CAST(#{hlpfulCnt} AS SIGNED),
        CAST(#{revScrValAvgVal} AS DECIMAL(10,2)),
        #{sysRegId}, now(), #{sysModId}, now()
    )
    ON DUPLICATE KEY UPDATE
        REV_CNT = CAST(#{revCnt} AS SIGNED),
        HLPFUL_CNT = CAST(#{hlpfulCnt} AS SIGNED),
        REV_STARSCR_AVG_VAL = CAST(#{revScrValAvgVal} AS DECIMAL(10,2)),
        SYS_MOD_ID = #{sysModId},
        SYS_MOD_DTM = now()
</insert>
```

### 후처리: 전시 요약 테이블 동기화 (CTE + Batch UPDATE)

```xml
<update id="syncGoodsSummaryRevCnt" parameterType="java.util.List">
    WITH REV_SUMMARY_INFO_LIST (GOODS_NO, REV_CNT, HLPFUL_CNT, SUM_SCR_VAL, REV_SCR_VAL_AVG_VAL) AS (
    <foreach collection="list" item="item" separator=" UNION ALL ">
        SELECT #{item.goodsNo}, CAST(#{item.revCnt} AS SIGNED),
               CAST(#{item.hlpfulCnt} AS SIGNED), CAST(#{item.sumScrVal} AS SIGNED),
               CAST(#{item.revScrValAvgVal} AS SIGNED)
    </foreach>
    )
    UPDATE pr_disp_goods_sumr_info PDGSI
    JOIN REV_SUMMARY_INFO_LIST RSII ON RSII.GOODS_NO = PDGSI.GOODS_NO
    SET GOODS_REV_CNT = RSII.REV_CNT,
        GOODS_REV_HLPFUL_CNT = RSII.HLPFUL_CNT,
        GOODS_REV_STARSCR_AVG_VAL = RSII.REV_SCR_VAL_AVG_VAL,
        SYS_MOD_ID = 'BATCH', SYS_MOD_DTM = NOW()
</update>
```

**참고 포인트**:
- `batchTyp` 파라미터로 증분(R/D/M) vs 전체(ALL) 선택 — 내 과제에서 `scope` 파라미터와 유사
- `LEFT JOIN LATERAL` — 상관 서브쿼리 패턴
- `foreach + CTE + JOIN UPDATE` — 벌크 UPDATE 패턴 (행 단위 루프 대신)

---

## 8. 커스텀 Lambda Writer (REST API 호출) 패턴

```java
// SearchProductChunkLoadConfig — CHUNK_SIZE=2000, BATCH_SIZE=200
private static final int CHUNK_SIZE = 2000;
private static final int BATCH_SIZE = 200;

@Bean
@StepScope
public ItemWriter<SearchProductLoadRequest> searchProductChunkLoadWriter() {
    return items -> {
        List<SearchProductLoadRequest> subList = new ArrayList<>(BATCH_SIZE);
        for (SearchProductLoadRequest item : items) {
            subList.add(item);
            if (subList.size() == BATCH_SIZE) {
                callSearchLoadApi(subList, item.getLangCd());
                subList.clear();
            }
        }
        if (!subList.isEmpty()) {
            callSearchLoadApi(subList, subList.get(0).getLangCd());
        }
    };
}

private void callSearchLoadApi(List<SearchProductLoadRequest> subList, String langCd) {
    Map<String, Object> requestData = new HashMap<>();
    requestData.put("langCd", langCd);
    requestData.put("data", subList);
    restApiUtil.post(searchApiUrl + "index/goods", requestData,
        new ParameterizedTypeReference<Response<Void>>() {});
    requestData.clear();
}
```

**참고 포인트**:
- Chunk(2000) 내에서 다시 서브 배치(200)로 분할 — API 호출 시 페이로드 크기 제어
- Lambda Writer로 DB가 아닌 외부 시스템에 쓰기

---

## 9. @StepScope + JobParameter 주입 패턴

```java
// Reader에서 파라미터 주입
@Bean
@StepScope
public MyBatisCursorItemReader<SearchProductLoadRequest> searchProductChunkLoadReader(
        @Value("#{jobParameters[intervalTime]}") String intervalTime,
        @Value("#{jobParameters[siteNo]}") String siteNo,
        @Value("#{jobParameters[langCd]}") String langCd) {

    SearchCommonParam commonParam = new SearchCommonParam();
    this.getBatchType(intervalTime, commonParam);
    commonParam.setSiteNo(siteNo);
    if (langCd != null && !langCd.isEmpty()) commonParam.setLangCd(langCd);

    Map<String, Object> paramMap = this.getSearchCommonParam(commonParam);
    return new MyBatisCursorItemReaderBuilder<SearchProductLoadRequest>()
            .sqlSessionFactory(searchRodbSqlSessionFactory)
            .queryId("...SearchMapper.getProductLoadInfoNew")
            .parameterValues(paramMap)
            .build();
}

// Step에서 파라미터 주입
@Bean
@JobScope
public Step mileageRemoveStep(@Value("#{jobParameters[batchDate]}") String batchDate) {
    this.batchDate = StringUtil.nvl(batchDate, DateUtil.today(X2Constants.YYYYMMDD));
    if (DateUtil.compareWithToday(this.batchDate) > 0) {
        throw new ValidationException("배치 처리 일자는 현재 일자보다 클 수 없습니다.");
    }
    return new StepBuilder("mileageRemoveStep", jobRepository)
            .<MbrAsstResponse, MileageExpireRequestVo>chunk(CHUNK_SIZE, transactionManager)
            .reader(mileageExpireListItemReader())
            .processor(mileageExpireListItemProcessor())
            .writer(mileageExpireCompositeItemWriter())
            .build();
}
```

**참고 포인트**:
- `@StepScope` Bean에서 `@Value("#{jobParameters[...]}")` 사용
- Step에서 파라미터 검증 (날짜 유효성 체크)
- `null` 체크 후 기본값 설정 패턴

---

## 10. 내 과제에 적용할 패턴 요약

| 내 과제 구성 요소 | 참고할 회사 코드 | 핵심 패턴 |
|------------------|----------------|----------|
| **Job 구성** | MemberGradeChangeConfig | Multi-Step + `.on("FAILED").end()` |
| **Reader** | SampleJdbcConfig | `JdbcCursorItemReaderBuilder` + `BeanPropertyRowMapper` |
| **Processor** | MemberGradeChangeConfig | DTO 변환 (Response → Request) |
| **Writer** | SampleJdbcConfig | `JdbcBatchItemWriterBuilder` + `itemPreparedStatementSetter` |
| **Cleanup Step** | SmDayclGoodsOrdAgrtTrxMapper | `DELETE WHERE period_key = ?` (Tasklet) |
| **파라미터 주입** | SearchProductChunkLoadConfig | `@StepScope` + `@Value("#{jobParameters[...]}")` |
| **중복 실행 방지** | SingleJobExecutionListener | `JobExplorer.findRunningJobExecutions()` |
| **Score 계산** | RankingCorrectionJobConfig (기존) | Score v2 공식 재활용 |
