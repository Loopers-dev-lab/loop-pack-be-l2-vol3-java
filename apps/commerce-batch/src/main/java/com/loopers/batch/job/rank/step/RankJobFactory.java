package com.loopers.batch.job.rank.step;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.MvProductRankPublicationRepository;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.RankPeriodType;
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
    public static final int CHUNK_SIZE = 20;
    public static final int TOP_N = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankRepository mvProductRankRepository;
    private final MvProductRankPublicationRepository mvProductRankPublicationRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Value("${batch.rank.validation.fail-on-incomplete:false}")
    private boolean failOnIncomplete;

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
        PublishingRankWriter writer = new PublishingRankWriter(
                mvProductRankRepository,
                mvProductRankPublicationRepository,
                periodType,
                periodKey,
                new TransactionTemplate(transactionManager),
                new TransactionTemplate(transactionManager)
        );

        return new StepBuilder(stepName, jobRepository)
                .<AggregatedScoreRow, AggregatedScoreRow>chunk(CHUNK_SIZE, transactionManager)
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
                    ps.setInt(3, TOP_N);
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
