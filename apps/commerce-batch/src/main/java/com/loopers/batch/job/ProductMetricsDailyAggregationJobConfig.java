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
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemStreamReader;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.support.AbstractItemStreamItemReader;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private static final BigDecimal VIEW_WEIGHT = BigDecimal.valueOf(0.1d);
    private static final BigDecimal LIKE_WEIGHT = BigDecimal.valueOf(0.2d);
    private static final BigDecimal SALES_AMOUNT_WEIGHT = BigDecimal.valueOf(0.7d);

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final DataSource dataSource;
    private final PlatformTransactionManager transactionManager;
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private final RedisTemplate<String, String> redisTemplate;
    private final Clock clock;

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

    public LocalDate productMetricsReferenceDate(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        if (requestDate != null) {
            return requestDate;
        }
        return LocalDate.now(clock).minusDays(1);
    }

    public PeriodRange productMetricsWeeklyPeriodRange(LocalDate referenceDate) {
        return new PeriodRange(referenceDate.minusDays(6), referenceDate);
    }

    public PeriodRange productMetricsMonthlyPeriodRange(LocalDate referenceDate) {
        return new PeriodRange(referenceDate.minusDays(29), referenceDate);
    }

    @JobScope
    @Bean(WEEKLY_CLEANUP_STEP_NAME)
    public Step productMetricsWeeklyCleanupStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        return createCleanupStep(
                WEEKLY_CLEANUP_STEP_NAME,
                "product_ranking_weekly_batch",
                productMetricsWeeklyPeriodRange(productMetricsReferenceDate(requestDate))
        );
    }

    @JobScope
    @Bean(MONTHLY_CLEANUP_STEP_NAME)
    public Step productMetricsMonthlyCleanupStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        return createCleanupStep(
                MONTHLY_CLEANUP_STEP_NAME,
                "product_ranking_monthly_batch",
                productMetricsMonthlyPeriodRange(productMetricsReferenceDate(requestDate))
        );
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
        PeriodRange weeklyRange = productMetricsWeeklyPeriodRange(productMetricsReferenceDate(requestDate));
        return createRedisSyncStep(
                WEEKLY_SYNC_STEP_NAME,
                "product_ranking_weekly_batch",
                buildWeeklyRankingKey(weeklyRange.endDate()),
                WEEKLY_RANKING_TTL,
                weeklyRange.endDate()
        );
    }

    @JobScope
    @Bean(MONTHLY_SYNC_STEP_NAME)
    public Step productMetricsMonthlyRedisSyncStep(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange monthlyRange = productMetricsMonthlyPeriodRange(productMetricsReferenceDate(requestDate));
        return createRedisSyncStep(
                MONTHLY_SYNC_STEP_NAME,
                "product_ranking_monthly_batch",
                buildMonthlyRankingKey(monthlyRange.endDate()),
                MONTHLY_RANKING_TTL,
                monthlyRange.endDate()
        );
    }

    @StepScope
    @Bean(WEEKLY_READER_NAME)
    public ItemStreamReader<AggregatedProductMetricsRow> productMetricsWeeklyAggregationReader(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        return createAggregationReader(
                WEEKLY_READER_NAME,
                productMetricsWeeklyPeriodRange(productMetricsReferenceDate(requestDate))
        );
    }

    @StepScope
    @Bean(MONTHLY_READER_NAME)
    public ItemStreamReader<AggregatedProductMetricsRow> productMetricsMonthlyAggregationReader(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        return createAggregationReader(
                MONTHLY_READER_NAME,
                productMetricsMonthlyPeriodRange(productMetricsReferenceDate(requestDate))
        );
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedProductMetricsRow, ProductRankingPeriodBatchRow> productMetricsWeeklyAggregationProcessor(
        @Value("#{jobParameters['requestDate']}") LocalDate requestDate
    ) {
        PeriodRange weeklyRange = productMetricsWeeklyPeriodRange(productMetricsReferenceDate(requestDate));
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
        PeriodRange monthlyRange = productMetricsMonthlyPeriodRange(productMetricsReferenceDate(requestDate));
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
                    "DELETE FROM %s WHERE period_end_date = ?".formatted(tableName),
                    Date.valueOf(periodRange.endDate())
                );
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    private Step createRedisSyncStep(String stepName, String tableName, String rankingKey, Duration ttl, LocalDate snapshotDate) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        return new StepBuilder(stepName, jobRepository)
                .tasklet((contribution, chunkContext) -> {
                    String tempRankingKey = rankingKey + ":sync";
                    redisTemplate.delete(tempRankingKey);
                    List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                            """
                            SELECT product_id, ranking_score
                            FROM %s
                            WHERE period_end_date = ?
                            ORDER BY ranking_score DESC, product_id ASC
                            LIMIT 100
                            """.formatted(tableName),
                            Date.valueOf(snapshotDate)
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

    private ItemStreamReader<AggregatedProductMetricsRow> createAggregationReader(String readerName, PeriodRange periodRange) {
        return new ApplicationAggregatingProductMetricsReader(readerName, dataSource, periodRange);
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
                    period_start_date = VALUES(period_start_date),
                    like_count = VALUES(like_count),
                    sales_count = VALUES(sales_count),
                    sales_amount = VALUES(sales_amount),
                    view_count = VALUES(view_count),
                    ranking_score = VALUES(ranking_score)
                """.formatted(tableName))
            .beanMapped()
            .build();
    }

    private String buildWeeklyRankingKey(LocalDate snapshotDate) {
        return "ranking:weekly:" + snapshotDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private String buildMonthlyRankingKey(LocalDate snapshotDate) {
        return "ranking:monthly:" + snapshotDate.format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE);
    }

    private static BigDecimal calculateRankingScore(MutableAggregation aggregation) {
        return BigDecimal.valueOf(aggregation.viewCount)
                .multiply(VIEW_WEIGHT)
                .add(BigDecimal.valueOf(aggregation.likeCount).multiply(LIKE_WEIGHT))
                .add(BigDecimal.valueOf(aggregation.salesAmount).multiply(SALES_AMOUNT_WEIGHT))
                .setScale(1, RoundingMode.HALF_UP);
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

    private static final class ApplicationAggregatingProductMetricsReader extends AbstractItemStreamItemReader<AggregatedProductMetricsRow> {
        private static final String CURRENT_INDEX_KEY = ".currentIndex";
        private static final String RAW_METRICS_QUERY = """
                SELECT product_id,
                       like_count,
                       sales_count,
                       sales_amount,
                       view_count
                FROM product_metrics_daily
                WHERE metric_date BETWEEN ? AND ?
                """;

        private final JdbcTemplate jdbcTemplate;
        private final PeriodRange periodRange;
        private List<AggregatedProductMetricsRow> items = List.of();
        private int currentIndex;

        private ApplicationAggregatingProductMetricsReader(String readerName, DataSource dataSource, PeriodRange periodRange) {
            this.jdbcTemplate = new JdbcTemplate(dataSource);
            this.periodRange = periodRange;
            setName(readerName);
        }

        @Override
        public void open(ExecutionContext executionContext) {
            items = loadAggregatedItems();
            currentIndex = executionContext.getInt(getExecutionContextKey(CURRENT_INDEX_KEY), 0);
        }

        @Override
        public AggregatedProductMetricsRow read() {
            if (currentIndex >= items.size()) {
                return null;
            }
            AggregatedProductMetricsRow item = items.get(currentIndex);
            currentIndex++;
            return item;
        }

        @Override
        public void update(ExecutionContext executionContext) {
            executionContext.putInt(getExecutionContextKey(CURRENT_INDEX_KEY), currentIndex);
        }

        @Override
        public void close() {
            items = List.of();
            currentIndex = 0;
        }

        private List<AggregatedProductMetricsRow> loadAggregatedItems() {
            Map<String, MutableAggregation> aggregationMap = new HashMap<>();
            jdbcTemplate.query(
                    RAW_METRICS_QUERY,
                    (RowCallbackHandler) rs -> {
                        String productId = rs.getString("product_id");
                        MutableAggregation aggregation = aggregationMap.computeIfAbsent(productId, key -> new MutableAggregation());
                        aggregation.likeCount += rs.getLong("like_count");
                        aggregation.salesCount += rs.getLong("sales_count");
                        aggregation.salesAmount += rs.getLong("sales_amount");
                        aggregation.viewCount += rs.getLong("view_count");
                    },
                    Date.valueOf(periodRange.startDate()),
                    Date.valueOf(periodRange.endDate())
            );

            return aggregationMap.entrySet().stream()
                    .map(entry -> new AggregatedProductMetricsRow(
                            entry.getKey(),
                            entry.getValue().likeCount,
                            entry.getValue().salesCount,
                            entry.getValue().salesAmount,
                            entry.getValue().viewCount,
                            calculateRankingScore(entry.getValue())
                    ))
                    .sorted(Comparator
                            .comparing(AggregatedProductMetricsRow::rankingScore, Comparator.reverseOrder())
                            .thenComparing(AggregatedProductMetricsRow::productId))
                    .limit(MAX_RANKING_ITEMS)
                    .collect(Collectors.toCollection(ArrayList::new));
        }
    }

    private static final class MutableAggregation {
        private long likeCount;
        private long salesCount;
        private long salesAmount;
        private long viewCount;
    }
}
