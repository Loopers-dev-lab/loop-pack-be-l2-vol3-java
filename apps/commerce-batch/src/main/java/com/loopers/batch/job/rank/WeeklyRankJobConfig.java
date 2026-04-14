package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.AggregatedScoreRow;
import com.loopers.batch.job.rank.step.AtomicMvRankWriter;
import com.loopers.batch.job.rank.step.RankAggregationSql;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.ranking.RankingKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankJobConfig {

    public static final String JOB_NAME = "weeklyRankJob";
    private static final String BUILD_STEP = "buildWeeklyRankStep";
    private static final int CHUNK_SIZE = 20;
    private static final int TOP_N = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankRepository mvProductRankRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Bean(JOB_NAME)
    public Job weeklyRankJob(Step buildWeeklyRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(buildWeeklyRankStep)
                .build();
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildWeeklyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        String periodKey = RankingKeyGenerator.weeklyPeriodKey(date);
        LocalDate weekStart = RankingKeyGenerator.weekStart(date);
        LocalDate weekEnd = RankingKeyGenerator.weekEnd(date);

        AtomicMvRankWriter writer = new AtomicMvRankWriter(
                mvProductRankRepository,
                RankPeriodType.WEEKLY,
                periodKey,
                new TransactionTemplate(transactionManager)
        );

        return new StepBuilder(BUILD_STEP, jobRepository)
                .<AggregatedScoreRow, AggregatedScoreRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyScoreReader(weekStart, weekEnd))
                .writer(writer)
                .listener(writer)
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    private JdbcCursorItemReader<AggregatedScoreRow> weeklyScoreReader(LocalDate weekStart, LocalDate weekEnd) {
        return new JdbcCursorItemReaderBuilder<AggregatedScoreRow>()
                .name("weeklyScoreReader")
                .dataSource(dataSource)
                .sql(RankAggregationSql.AGGREGATE_BY_DATE_RANGE)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, weekStart);
                    ps.setObject(2, weekEnd);
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
