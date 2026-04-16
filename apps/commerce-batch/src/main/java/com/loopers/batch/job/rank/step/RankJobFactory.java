package com.loopers.batch.job.rank.step;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.MvProductRankPublicationRepository;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.RankPeriodType;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
@RequiredArgsConstructor
public class RankJobFactory {

    public static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankRepository mvProductRankRepository;
    private final MvProductRankPublicationRepository mvProductRankPublicationRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final MeterRegistry meterRegistry;

    @Value("${batch.rank.validation.fail-on-incomplete:false}")
    private boolean failOnIncomplete;

    @Value("${batch.rank.health-check.min-rows:1}")
    private long healthCheckMinRows;

    @Value("${batch.rank.health-check.max-variance-pct:0.5}")
    private double healthCheckMaxVariancePct;

    @Value("${batch.rank.health-check.fail-on-anomaly:false}")
    private boolean healthCheckFailOnAnomaly;

    @Value("${batch.rank.cleanup.batch-limit:1000}")
    private int cleanupBatchLimit;

    @Value("${batch.rank.top-n:100}")
    private int topN;

    @Value("${batch.rank.chunk-size:20}")
    private int chunkSize;

    public Job buildJob(String jobName, Step step) {
        return new JobBuilder(jobName, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(step)
                .build();
    }

    public Job buildJob(String jobName, Step validationStep, Step buildStep) {
        return new JobBuilder(jobName, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(validationStep)
                .next(buildStep)
                .build();
    }

    public Job buildJob(String jobName, Step validationStep, Step buildStep, Step healthCheckStep, Step cleanupStep) {
        return new JobBuilder(jobName, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(validationStep)
                .next(buildStep)
                .next(healthCheckStep)
                .next(cleanupStep)
                .build();
    }

    public Step buildHealthCheckStep(String stepName,
                                      RankPeriodType periodType,
                                      String currentPeriodKey,
                                      String previousPeriodKey) {
        return buildHealthCheckStep(stepName, periodType, currentPeriodKey, previousPeriodKey, false);
    }

    public Step buildHealthCheckStep(String stepName,
                                      RankPeriodType periodType,
                                      String currentPeriodKey,
                                      String previousPeriodKey,
                                      boolean backfillMode) {
        MvOutputHealthCheckTasklet tasklet = new MvOutputHealthCheckTasklet(
                new JdbcTemplate(dataSource),
                periodType,
                currentPeriodKey,
                previousPeriodKey,
                healthCheckMinRows,
                healthCheckMaxVariancePct,
                healthCheckFailOnAnomaly,
                backfillMode
        );
        return new StepBuilder(stepName, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    public Step buildCleanupStep(String stepName, RankPeriodType periodType, String periodKey) {
        MvRankCleanupTasklet tasklet = new MvRankCleanupTasklet(
                new JdbcTemplate(dataSource),
                periodType,
                periodKey,
                cleanupBatchLimit,
                meterRegistry
        );
        return new StepBuilder(stepName, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    public Step buildValidationStep(String stepName, LocalDate periodStart, LocalDate periodEnd) {
        ScoreCompletenessTasklet tasklet = new ScoreCompletenessTasklet(
                new JdbcTemplate(dataSource), periodStart, periodEnd, failOnIncomplete
        );
        return new StepBuilder(stepName, jobRepository)
                .tasklet(tasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    public Step buildStep(String stepName,
                          RankPeriodType periodType,
                          String periodKey,
                          LocalDate periodStart,
                          LocalDate periodEnd) {
        TransactionTemplate insertTx = new TransactionTemplate(transactionManager);
        insertTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        TransactionTemplate publishTx = new TransactionTemplate(transactionManager);
        publishTx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
        PublishingRankWriter writer = new PublishingRankWriter(
                mvProductRankRepository,
                mvProductRankPublicationRepository,
                periodType,
                periodKey,
                insertTx,
                publishTx,
                meterRegistry
        );

        return new StepBuilder(stepName, jobRepository)
                .<AggregatedScoreRow, AggregatedScoreRow>chunk(chunkSize, transactionManager)
                .reader(buildReader(stepName + "Reader", periodStart, periodEnd))
                .writer(writer)
                .listener(writer)
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    private JdbcCursorItemReader<AggregatedScoreRow> buildReader(String name, LocalDate start, LocalDate end) {
        return new JdbcCursorItemReaderBuilder<AggregatedScoreRow>()
                .name(name)
                .dataSource(dataSource)
                .sql(RankAggregationSql.AGGREGATE_BY_DATE_RANGE)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, start);
                    ps.setObject(2, end);
                    ps.setInt(3, topN);
                })
                .rowMapper((rs, rowNum) -> new AggregatedScoreRow(
                        rs.getLong("product_db_id"),
                        rs.getDouble("total_score"),
                        rs.getLong("total_view"),
                        rs.getLong("total_like"),
                        rs.getBigDecimal("total_order")
                ))
                .build();
    }
}
