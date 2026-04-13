package com.loopers.batch.job;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.config.redis.RedisConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcPagingItemReaderBuilder;
import org.springframework.batch.item.database.support.MySqlPagingQueryProvider;
import org.springframework.batch.item.database.Order;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Configuration
public class ProductMetricsDailyAggregationJobConfig {
    public static final String WEEKLY_JOB_NAME = "productMetricsWeeklyAggregationJob";
    public static final String MONTHLY_JOB_NAME = "productMetricsMonthlyAggregationJob";

    private static final String WEEKLY_CLEANUP_STEP_NAME = "productMetricsWeeklyCleanupStep";
    private static final String MONTHLY_CLEANUP_STEP_NAME = "productMetricsMonthlyCleanupStep";
    private static final String WEEKLY_STEP_NAME = "productMetricsWeeklyAggregationStep";
    private static final String MONTHLY_STEP_NAME = "productMetricsMonthlyAggregationStep";
    private static final String WEEKLY_SYNC_STEP_NAME = "productMetricsWeeklyRedisSyncStep";
    private static final String MONTHLY_SYNC_STEP_NAME = "productMetricsMonthlyRedisSyncStep";
    private static final String WEEKLY_READER_NAME = "productMetricsWeeklyAggregationReader";
    private static final String MONTHLY_READER_NAME = "productMetricsMonthlyAggregationReader";
    private static final String WEEKLY_WRITER_NAME = "productMetricsWeeklyAggregationWriter";
    private static final String MONTHLY_WRITER_NAME = "productMetricsMonthlyAggregationWriter";
    private static final int CHUNK_SIZE = 100;
    private static final int MAX_RANKING_ITEMS = 100;
    private static final Duration WEEKLY_RANKING_TTL = Duration.ofDays(90);
    private static final Duration MONTHLY_RANKING_TTL = Duration.ofDays(400);

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;

    @Bean(WEEKLY_JOB_NAME)
    @ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WEEKLY_JOB_NAME)
    public Job productMetricsWeeklyAggregationJob() {
        return new JobBuilder(WEEKLY_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(productMetricsWeeklyCleanupStep(null))
            .next(productMetricsWeeklyAggregationStep())
            .next(productMetricsWeeklyRedisSyncStep(null))
            .listener(jobListener)
            .build();
    }

    @Bean(MONTHLY_JOB_NAME)
    @ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MONTHLY_JOB_NAME)
    public Job productMetricsMonthlyAggregationJob() {
        return new JobBuilder(MONTHLY_JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(productMetricsMonthlyCleanupStep(null))
            .next(productMetricsMonthlyAggregationStep())
            .next(productMetricsMonthlyRedisSyncStep(null))
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(WEEKLY_CLEANUP_STEP_NAME)
    public Step productMetricsWeeklyCleanupStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange weeklyRange = weeklyRange(requestDate);
        return createCleanupStep(WEEKLY_CLEANUP_STEP_NAME, "product_ranking_weekly_batch", weeklyRange);
    }

    @JobScope
    @Bean(MONTHLY_CLEANUP_STEP_NAME)
    public Step productMetricsMonthlyCleanupStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange monthlyRange = monthlyRange(requestDate);
        return createCleanupStep(MONTHLY_CLEANUP_STEP_NAME, "product_ranking_monthly_batch", monthlyRange);
    }

    @JobScope
    @Bean(WEEKLY_STEP_NAME)
    public Step productMetricsWeeklyAggregationStep() {
        return new StepBuilder(WEEKLY_STEP_NAME, jobRepository)
            .<AggregatedProductMetricsRow, ProductRankingPeriodBatchRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(productMetricsWeeklyAggregationReader(null))
            .processor(productMetricsWeeklyAggregationProcessor(null))
            .writer(productMetricsWeeklyAggregationWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @JobScope
    @Bean(MONTHLY_STEP_NAME)
    public Step productMetricsMonthlyAggregationStep() {
        return new StepBuilder(MONTHLY_STEP_NAME, jobRepository)
            .<AggregatedProductMetricsRow, ProductRankingPeriodBatchRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(productMetricsMonthlyAggregationReader(null))
            .processor(productMetricsMonthlyAggregationProcessor(null))
            .writer(productMetricsMonthlyAggregationWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @JobScope
    @Bean(WEEKLY_SYNC_STEP_NAME)
    public Step productMetricsWeeklyRedisSyncStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange weeklyRange = weeklyRange(requestDate);
        return createRedisSyncStep(
                WEEKLY_SYNC_STEP_NAME,
                "product_ranking_weekly_batch",
                buildWeeklyRankingKey(weeklyRange.startDate()),
                WEEKLY_RANKING_TTL,
                weeklyRange.startDate()
        );
    }

    @JobScope
    @Bean(MONTHLY_SYNC_STEP_NAME)
    public Step productMetricsMonthlyRedisSyncStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange monthlyRange = monthlyRange(requestDate);
        return createRedisSyncStep(
                MONTHLY_SYNC_STEP_NAME,
                "product_ranking_monthly_batch",
                buildMonthlyRankingKey(monthlyRange.startDate()),
                MONTHLY_RANKING_TTL,
                monthlyRange.startDate()
        );
    }

    @StepScope
    @Bean(WEEKLY_READER_NAME)
    public JdbcPagingItemReader<AggregatedProductMetricsRow> productMetricsWeeklyAggregationReader(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange weeklyRange = weeklyRange(requestDate);
        return createAggregationReader(WEEKLY_READER_NAME, weeklyRange);
    }

    @StepScope
    @Bean(MONTHLY_READER_NAME)
    public JdbcPagingItemReader<AggregatedProductMetricsRow> productMetricsMonthlyAggregationReader(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange monthlyRange = monthlyRange(requestDate);
        return createAggregationReader(MONTHLY_READER_NAME, monthlyRange);
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedProductMetricsRow, ProductRankingPeriodBatchRow> productMetricsWeeklyAggregationProcessor(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange weeklyRange = weeklyRange(requestDate);
        return item -> new ProductRankingPeriodBatchRow(
            weeklyRange.startDate(),
            weeklyRange.endDate(),
            item.productId(),
            item.likeCount(),
            item.salesCount(),
            item.salesAmount(),
            item.viewCount(),
            item.rankingScore()
        );
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedProductMetricsRow, ProductRankingPeriodBatchRow> productMetricsMonthlyAggregationProcessor(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange monthlyRange = monthlyRange(requestDate);
        return item -> new ProductRankingPeriodBatchRow(
            monthlyRange.startDate(),
            monthlyRange.endDate(),
            item.productId(),
            item.likeCount(),
            item.salesCount(),
            item.salesAmount(),
            item.viewCount(),
            item.rankingScore()
        );
    }

    @Bean(WEEKLY_WRITER_NAME)
    public JdbcBatchItemWriter<ProductRankingPeriodBatchRow> productMetricsWeeklyAggregationWriter() {
        return createAggregationWriter("product_ranking_weekly_batch");
    }

    @Bean(MONTHLY_WRITER_NAME)
    public JdbcBatchItemWriter<ProductRankingPeriodBatchRow> productMetricsMonthlyAggregationWriter() {
        return createAggregationWriter("product_ranking_monthly_batch");
    }

    private Step createCleanupStep(String stepName, String tableName, PeriodRange periodRange) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return new StepBuilder(stepName, jobRepository)
            .tasklet((contribution, chunkContext) -> {
                jdbcTemplate.update(
                    "DELETE FROM %s WHERE period_start_date = ?".formatted(tableName),
                    Date.valueOf(periodRange.startDate())
                );
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    private Step createRedisSyncStep(String stepName, String tableName, String rankingKey, Duration ttl, LocalDate periodStartDate) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return new StepBuilder(stepName, jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String tempRankingKey = rankingKey + ":sync";
                    redisTemplate.delete(tempRankingKey);
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            """
                            SELECT product_id, ranking_score
                            FROM %s
                            WHERE period_start_date = ?
                            ORDER BY ranking_score DESC, product_id ASC
                            LIMIT 100
                            """.formatted(tableName),
                            Date.valueOf(periodStartDate)
                    );
                    if (rows.isEmpty()) {
                        redisTemplate.delete(rankingKey);
                        return RepeatStatus.FINISHED;
                    }
                    Set<ZSetOperations.TypedTuple<String>> tuples = rows.stream()
                            .map(row -> ZSetOperations.TypedTuple.of(
                                    (String) row.get("product_id"),
                                    ((Number) row.get("ranking_score")).doubleValue()
                            ))
                            .collect(Collectors.toSet());
                    redisTemplate.opsForZSet().add(tempRankingKey, tuples);
                    redisTemplate.expire(tempRankingKey, ttl);
                    redisTemplate.rename(tempRankingKey, rankingKey);
                    redisTemplate.expire(rankingKey, ttl);
                    return RepeatStatus.FINISHED;
                }, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    private JdbcPagingItemReader<AggregatedProductMetricsRow> createAggregationReader(String readerName, PeriodRange periodRange) {
        var queryProvider = new MySqlPagingQueryProvider();
        queryProvider.setSelectClause("""
            SELECT product_id,
                   SUM(like_count) AS like_count,
                   SUM(sales_count) AS sales_count,
                   SUM(sales_amount) AS sales_amount,
                   SUM(view_count) AS view_count,
                   ROUND(
                       SUM(view_count) * 0.1
                       + SUM(like_count) * 0.2
                       + SUM(sales_amount) * 0.7,
                       1
                   ) AS ranking_score
            """);
        queryProvider.setFromClause("FROM product_metrics_daily");
        queryProvider.setWhereClause("WHERE metric_date BETWEEN :startDate AND :endDate");
        queryProvider.setGroupClause("GROUP BY product_id");

        var sortKeys = new LinkedHashMap<String, Order>();
        sortKeys.put("ranking_score", Order.DESCENDING);
        sortKeys.put("product_id", Order.ASCENDING);
        queryProvider.setSortKeys(sortKeys);

        Map<String, Object> parameterValues = Map.of(
            "startDate", Date.valueOf(periodRange.startDate()),
            "endDate", Date.valueOf(periodRange.endDate())
        );

        return new JdbcPagingItemReaderBuilder<AggregatedProductMetricsRow>()
            .name(readerName)
            .dataSource(dataSource)
            .pageSize(CHUNK_SIZE)
            .fetchSize(CHUNK_SIZE)
            .maxItemCount(MAX_RANKING_ITEMS)
            .queryProvider(queryProvider)
            .parameterValues(parameterValues)
            .rowMapper((rs, rowNum) -> new AggregatedProductMetricsRow(
                rs.getString("product_id"),
                rs.getLong("like_count"),
                rs.getLong("sales_count"),
                rs.getLong("sales_amount"),
                rs.getLong("view_count"),
                rs.getBigDecimal("ranking_score")
            ))
            .build();
    }

    private JdbcBatchItemWriter<ProductRankingPeriodBatchRow> createAggregationWriter(String tableName) {
        return new JdbcBatchItemWriterBuilder<ProductRankingPeriodBatchRow>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO %s (
                    period_start_date,
                    period_end_date,
                    product_id,
                    like_count,
                    sales_count,
                    sales_amount,
                    view_count,
                    ranking_score
                ) VALUES (
                    :periodStartDate,
                    :periodEndDate,
                    :productId,
                    :likeCount,
                    :salesCount,
                    :salesAmount,
                    :viewCount,
                    :rankingScore
                )
                ON DUPLICATE KEY UPDATE
                    period_end_date = VALUES(period_end_date),
                    like_count = VALUES(like_count),
                    sales_count = VALUES(sales_count),
                    sales_amount = VALUES(sales_amount),
                    view_count = VALUES(view_count),
                    ranking_score = VALUES(ranking_score)
                """.formatted(tableName))
            .beanMapped()
            .build();
    }

    private PeriodRange weeklyRange(LocalDate requestDate) {
        LocalDate baseDate = requiredRequestDate(requestDate);
        LocalDate startDate = baseDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return new PeriodRange(startDate, startDate.plusDays(6));
    }

    private PeriodRange monthlyRange(LocalDate requestDate) {
        LocalDate baseDate = requiredRequestDate(requestDate);
        LocalDate startDate = baseDate.withDayOfMonth(1);
        return new PeriodRange(startDate, startDate.with(TemporalAdjusters.lastDayOfMonth()));
    }

    private String buildWeeklyRankingKey(LocalDate periodStartDate) {
        return "ranking:weekly:" + periodStartDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String buildMonthlyRankingKey(LocalDate periodStartDate) {
        return "ranking:monthly:" + periodStartDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private LocalDate requiredRequestDate(LocalDate requestDate) {
        if (requestDate == null) {
            throw new IllegalArgumentException("requestDate job parameter is required");
        }
        return requestDate;
    }

    public record PeriodRange(
        LocalDate startDate,
        LocalDate endDate
    ) {
    }

    public record AggregatedProductMetricsRow(
        String productId,
        long likeCount,
        long salesCount,
        long salesAmount,
        long viewCount,
        BigDecimal rankingScore
    ) {
    }

    public record ProductRankingPeriodBatchRow(
        LocalDate periodStartDate,
        LocalDate periodEndDate,
        String productId,
        long likeCount,
        long salesCount,
        long salesAmount,
        long viewCount,
        BigDecimal rankingScore
    ) {
    }
}
