package com.loopers.batch.job.rankingmv;

import com.loopers.batch.job.rankingmv.step.CleanupTasklet;
import com.loopers.batch.job.rankingcorrection.RankingCorrectionProperties;
import com.loopers.domain.ranking.ScoreFormula;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * MV 기반 주간/월간 랭킹 집계 Job.
 *
 * <p>product_metrics를 product_id 범위로 분할하여 병렬 집계(스테이징)한 후,
 * mergeStep에서 Global TOP 100을 추출하여 MV 테이블에 적재한다.</p>
 *
 * <p>Score 계산은 SQL이 아닌 Java ItemProcessor에서 {@link ScoreFormula}를 사용하여
 * 모든 Score 경로(streamer, batch correction, MV)와 공식을 통일한다.</p>
 */
@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankingMvJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankingMvJobConfig {

    public static final String JOB_NAME = "productRankingMvJob";
    private static final int CHUNK_SIZE = 1_000;
    @Value("${ranking.mv.grid-size:4}")
    private int gridSize;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final CleanupTasklet cleanupTasklet;
    private final RankingCorrectionProperties properties;

    @Bean(JOB_NAME)
    public Job productRankingMvJob(
        @Qualifier("cleanupStep") Step cleanupStep,
        @Qualifier("partitionedAggregateStep") Step partitionedAggregateStep,
        @Qualifier("mergeStep") Step mergeStep
    ) {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(cleanupStep).on("FAILED").end()
            .from(cleanupStep).on("*").to(partitionedAggregateStep)
            .next(mergeStep)
            .end()
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean("cleanupStep")
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet(cleanupTasklet, transactionManager)
            .allowStartIfComplete(true)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean("partitionedAggregateStep")
    public Step partitionedAggregateStep(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope
    ) {
        return new StepBuilder("partitionedAggregateStep", jobRepository)
            .partitioner("workerStep", createPartitioner(targetDate, scope))
            .step(workerStep())
            .gridSize(gridSize)
            .taskExecutor(new SimpleAsyncTaskExecutor("mv-worker-"))
            .build();
    }

    private Partitioner createPartitioner(String targetDate, String scope) {
        return gridSize -> {
            int days = "weekly".equals(scope) ? 6 : 29;
            LocalDate endDate = LocalDate.parse(targetDate, DATE_FORMATTER);
            LocalDate startDate = endDate.minusDays(days);

            List<Long> productIds = jdbcTemplate.queryForList(
                "SELECT DISTINCT product_id FROM product_metrics " +
                "WHERE metric_date BETWEEN ? AND ? ORDER BY product_id",
                Long.class, startDate, endDate);

            if (productIds.isEmpty()) {
                log.warn("[Partitioner] 데이터 없음: {} ~ {}", startDate, endDate);
                Map<String, ExecutionContext> empty = new HashMap<>();
                ExecutionContext ctx = new ExecutionContext();
                ctx.putLong("minProductId", 0);
                ctx.putLong("maxProductId", 0);
                empty.put("partition0", ctx);
                return empty;
            }

            int totalProducts = productIds.size();
            int partitionSize = totalProducts / gridSize + (totalProducts % gridSize == 0 ? 0 : 1);
            Map<String, ExecutionContext> partitions = new HashMap<>();

            for (int i = 0; i < gridSize; i++) {
                int fromIndex = i * partitionSize;
                if (fromIndex >= totalProducts) break;
                int toIndex = Math.min((i + 1) * partitionSize, totalProducts);

                ExecutionContext ctx = new ExecutionContext();
                long partMin = productIds.get(fromIndex);
                long partMax = productIds.get(toIndex - 1);
                ctx.putLong("minProductId", partMin);
                ctx.putLong("maxProductId", partMax);
                partitions.put("partition" + i, ctx);

                log.info("[Partitioner] partition{}: productId {}~{} ({}건)", i, partMin, partMax, toIndex - fromIndex);
            }
            return partitions;
        };
    }

    @Bean
    public Step workerStep() {
        ExponentialBackOffPolicy backOff = new ExponentialBackOffPolicy();
        backOff.setInitialInterval(100);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(1000);

        return new StepBuilder("workerStep", jobRepository)
            .<AggregatedMetricsRow, ScoredProductRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(stagingReader(null, null, null, null))
            .processor(scoringProcessor())
            .writer(stagingWriter(null))
            .faultTolerant()
                .retry(DeadlockLoserDataAccessException.class)
                .retry(TransientDataAccessException.class)
                .retryLimit(3)
                .backOffPolicy(backOff)
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<AggregatedMetricsRow> stagingReader(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope,
        @Value("#{stepExecutionContext['minProductId']}") Long minProductId,
        @Value("#{stepExecutionContext['maxProductId']}") Long maxProductId
    ) {
        int days = "weekly".equals(scope) ? 6 : 29;
        LocalDate endDate = LocalDate.parse(targetDate, DATE_FORMATTER);
        LocalDate startDate = endDate.minusDays(days);

        String sql = """
            SELECT
                pm.product_id,
                SUM(pm.view_count) AS total_view_count,
                SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
                SUM(pm.sales_count) AS total_sales_count,
                SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
                p.category_id
            FROM product_metrics pm
            JOIN product p ON pm.product_id = p.id
            WHERE pm.metric_date BETWEEN ? AND ?
              AND pm.product_id BETWEEN ? AND ?
              AND p.deleted_at IS NULL
            GROUP BY pm.product_id, p.category_id
            """;

        return new JdbcCursorItemReaderBuilder<AggregatedMetricsRow>()
            .name("stagingReader")
            .dataSource(dataSource)
            .sql(sql)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, startDate);
                ps.setObject(2, endDate);
                ps.setLong(3, minProductId);
                ps.setLong(4, maxProductId);
            })
            .rowMapper((rs, rowNum) -> new AggregatedMetricsRow(
                rs.getLong("product_id"),
                rs.getLong("total_view_count"),
                rs.getLong("total_net_like_count"),
                rs.getLong("total_sales_count"),
                rs.getLong("total_net_sales_amount"),
                rs.getObject("category_id") != null ? rs.getLong("category_id") : null
            ))
            .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<AggregatedMetricsRow, ScoredProductRow> scoringProcessor() {
        long nowEpochSeconds = Instant.now().getEpochSecond();
        return row -> {
            int categoryPriority = resolveCategoryPriority(row.categoryId());
            double score = ScoreFormula.calculate(
                row.viewCount(), row.likeCount(), row.salesAmount(),
                categoryPriority, nowEpochSeconds, properties.weights());
            return new ScoredProductRow(row.productId(), score,
                row.viewCount(), row.likeCount(), row.salesCount(), row.salesAmount());
        };
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ScoredProductRow> stagingWriter(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        return new JdbcBatchItemWriterBuilder<ScoredProductRow>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO mv_product_rank_staging
                (product_id, score, view_count, like_count, sales_count, sales_amount, period_key)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
            .itemPreparedStatementSetter((item, ps) -> {
                ps.setLong(1, item.productId());
                ps.setDouble(2, item.score());
                ps.setLong(3, item.viewCount());
                ps.setLong(4, item.likeCount());
                ps.setLong(5, item.salesCount());
                ps.setLong(6, item.salesAmount());
                ps.setString(7, targetDate);
            })
            .assertUpdates(false)
            .build();
    }

    @JobScope
    @Bean("mergeStep")
    public Step mergeStep(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope
    ) {
        return new StepBuilder("mergeStep", jobRepository)
            .tasklet((contribution, chunkContext) -> {
                String mvTable = switch (scope) {
                    case "weekly" -> "mv_product_rank_weekly";
                    case "monthly" -> "mv_product_rank_monthly";
                    default -> throw new IllegalArgumentException("Invalid scope: " + scope);
                };

                int inserted = jdbcTemplate.update("""
                    INSERT INTO %s
                        (product_id, ranking, score, view_count, like_count,
                         sales_count, sales_amount, period_key, created_at)
                    SELECT
                        product_id,
                        ROW_NUMBER() OVER (ORDER BY score DESC) AS ranking,
                        score, view_count, like_count, sales_count, sales_amount,
                        ?, NOW()
                    FROM mv_product_rank_staging
                    WHERE period_key = ?
                    ORDER BY score DESC
                    LIMIT 100
                    """.formatted(mvTable), targetDate, targetDate);

                log.info("[Merge] {} 적재 완료: period_key={}, rows={}", mvTable, targetDate, inserted);
                return RepeatStatus.FINISHED;
            }, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    private int resolveCategoryPriority(Long categoryId) {
        if (categoryId == null) return properties.defaultCategoryPriority();
        return properties.categoryPriority()
            .getOrDefault(categoryId, properties.defaultCategoryPriority());
    }

    record AggregatedMetricsRow(
        long productId, long viewCount, long likeCount,
        long salesCount, long salesAmount, Long categoryId
    ) {}

    record ScoredProductRow(
        long productId, double score,
        long viewCount, long likeCount,
        long salesCount, long salesAmount
    ) {}
}
