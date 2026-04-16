package com.loopers.batch.job.rankingmv;

import com.loopers.batch.job.rankingmv.step.CleanupTasklet;
import com.loopers.batch.job.rankingcorrection.RankingCorrectionProperties;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * MV 기반 주간/월간 랭킹 집계 Job (Partitioning + Map-Reduce).
 *
 * <p>product_metrics를 product_id 범위로 분할하여 병렬 집계(스테이징)한 후,
 * mergeStep에서 Global TOP 100을 추출하여 MV 테이블에 적재한다.</p>
 *
 * <p>Score 수식 (v2 — 균등 합산): Reader SQL에서 LOG10 기반 계산.
 * 기간 내 메트릭을 합산한 뒤 score를 1회 계산하므로, Redis(지수 감쇠)와 다른 관점의 랭킹을 제공한다.</p>
 */
@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = ProductRankingMvJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class ProductRankingMvJobConfig {

    public static final String JOB_NAME = "productRankingMvJob";
    private static final int CHUNK_SIZE = 1_000;
    private static final int GRID_SIZE = 4;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final CleanupTasklet cleanupTasklet;
    private final RankingCorrectionProperties properties;

    // ── Job ──────────────────────────────────────────────────────────────

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

    // ── Step 1: Cleanup ──────────────────────────────────────────────────

    @JobScope
    @Bean("cleanupStep")
    public Step cleanupStep() {
        return new StepBuilder("cleanupStep", jobRepository)
            .tasklet(cleanupTasklet, transactionManager)
            .allowStartIfComplete(true)
            .listener(stepMonitorListener)
            .build();
    }

    // ── Step 2: Partitioned Aggregate ────────────────────────────────────

    @JobScope
    @Bean("partitionedAggregateStep")
    public Step partitionedAggregateStep(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope
    ) {
        return new StepBuilder("partitionedAggregateStep", jobRepository)
            .partitioner("workerStep", productIdPartitioner(targetDate, scope))
            .step(workerStep())
            .gridSize(GRID_SIZE)
            .taskExecutor(new SimpleAsyncTaskExecutor("mv-worker-"))
            .build();
    }

    @Bean
    public Partitioner productIdPartitioner(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope
    ) {
        return gridSize -> {
            int days = "weekly".equals(scope) ? 6 : 29;
            LocalDate endDate = LocalDate.parse(targetDate, DATE_FORMATTER);
            LocalDate startDate = endDate.minusDays(days);

            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            Long minId = jdbc.queryForObject(
                "SELECT COALESCE(MIN(product_id), 0) FROM product_metrics " +
                "WHERE metric_date BETWEEN ? AND ?",
                Long.class, startDate, endDate);
            Long maxId = jdbc.queryForObject(
                "SELECT COALESCE(MAX(product_id), 0) FROM product_metrics " +
                "WHERE metric_date BETWEEN ? AND ?",
                Long.class, startDate, endDate);

            if (minId == null || maxId == null || maxId == 0) {
                log.warn("[Partitioner] 데이터 없음: {} ~ {}", startDate, endDate);
                Map<String, ExecutionContext> empty = new HashMap<>();
                ExecutionContext ctx = new ExecutionContext();
                ctx.putLong("minProductId", 0);
                ctx.putLong("maxProductId", 0);
                empty.put("partition0", ctx);
                return empty;
            }

            long range = (maxId - minId) / gridSize + 1;
            Map<String, ExecutionContext> partitions = new HashMap<>();

            for (int i = 0; i < gridSize; i++) {
                ExecutionContext ctx = new ExecutionContext();
                long partMin = minId + (i * range);
                long partMax = Math.min(minId + ((i + 1) * range) - 1, maxId);
                ctx.putLong("minProductId", partMin);
                ctx.putLong("maxProductId", partMax);
                partitions.put("partition" + i, ctx);

                log.info("[Partitioner] partition{}: productId {}~{}", i, partMin, partMax);
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
            .<ScoredProductRow, ScoredProductRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(stagingReader(null, null, null, null))
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
    public JdbcCursorItemReader<ScoredProductRow> stagingReader(
        @Value("#{jobParameters['targetDate']}") String targetDate,
        @Value("#{jobParameters['scope']}") String scope,
        @Value("#{stepExecutionContext['minProductId']}") Long minProductId,
        @Value("#{stepExecutionContext['maxProductId']}") Long maxProductId
    ) {
        int days = "weekly".equals(scope) ? 6 : 29;
        LocalDate endDate = LocalDate.parse(targetDate, DATE_FORMATTER);
        LocalDate startDate = endDate.minusDays(days);

        RankingCorrectionProperties.Weights w = properties.weights();

        String sql = """
            SELECT
                pm.product_id,
                SUM(pm.view_count) AS total_view_count,
                SUM(pm.like_count - pm.unlike_count) AS total_net_like_count,
                SUM(pm.sales_count) AS total_sales_count,
                SUM(pm.sales_amount - pm.cancel_amount_by_event_date) AS total_net_sales_amount,
                (
                    %s * LOG10(GREATEST(SUM(pm.view_count), 0) + 1) / 7.0
                  + %s * LOG10(GREATEST(SUM(pm.like_count - pm.unlike_count), 0) + 1) / 7.0
                  + %s * LOG10(GREATEST(SUM(pm.sales_amount - pm.cancel_amount_by_event_date), 0) + 1) / 7.0
                  + UNIX_TIMESTAMP() * 1e-16
                ) AS score
            FROM product_metrics pm
            JOIN product p ON pm.product_id = p.id
            WHERE pm.metric_date BETWEEN ? AND ?
              AND pm.product_id BETWEEN ? AND ?
              AND p.deleted_at IS NULL
            GROUP BY pm.product_id
            """.formatted(w.view(), w.like(), w.order());

        return new JdbcCursorItemReaderBuilder<ScoredProductRow>()
            .name("stagingReader")
            .dataSource(dataSource)
            .sql(sql)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, startDate);
                ps.setObject(2, endDate);
                ps.setLong(3, minProductId);
                ps.setLong(4, maxProductId);
            })
            .rowMapper((rs, rowNum) -> new ScoredProductRow(
                rs.getLong("product_id"),
                rs.getDouble("score"),
                rs.getLong("total_view_count"),
                rs.getLong("total_net_like_count"),
                rs.getLong("total_sales_count"),
                rs.getLong("total_net_sales_amount")
            ))
            .build();
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

    // ── Step 3: Merge ────────────────────────────────────────────────────

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

                JdbcTemplate jdbc = new JdbcTemplate(dataSource);
                int inserted = jdbc.update("""
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

    // ── DTO ──────────────────────────────────────────────────────────────

    record ScoredProductRow(
        long productId, double score,
        long viewCount, long likeCount,
        long salesCount, long salesAmount
    ) {}
}
