package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.AggregatedScoreRow;
import com.loopers.batch.job.rank.step.AtomicMvRankWriter;
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

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankJobConfig {

    public static final String JOB_NAME = "monthlyRankJob";
    private static final String BUILD_STEP = "buildMonthlyRankStep";
    private static final int CHUNK_SIZE = 20;
    private static final int TOP_N = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private static final String READER_SQL = """
            SELECT sd.product_db_id,
                   SUM(sd.score)          AS total_score,
                   SUM(sd.view_count)     AS total_view,
                   SUM(sd.like_count)     AS total_like,
                   SUM(sd.order_amount)   AS total_order
            FROM mv_product_score_daily sd
            WHERE sd.score_date BETWEEN ? AND ?
            GROUP BY sd.product_db_id
            ORDER BY total_score DESC, sd.product_db_id ASC
            LIMIT ?
            """;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final MvProductRankRepository mvProductRankRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Bean(JOB_NAME)
    public Job monthlyRankJob(Step buildMonthlyRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(buildMonthlyRankStep)
                .build();
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildMonthlyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        String periodKey = RankingKeyGenerator.monthlyPeriodKey(date);
        LocalDate monthStart = RankingKeyGenerator.monthStart(date);
        LocalDate monthEnd = RankingKeyGenerator.monthEnd(date);

        AtomicMvRankWriter writer = new AtomicMvRankWriter(
                mvProductRankRepository,
                RankPeriodType.MONTHLY,
                periodKey,
                new TransactionTemplate(transactionManager)
        );

        return new StepBuilder(BUILD_STEP, jobRepository)
                .<AggregatedScoreRow, AggregatedScoreRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyScoreReader(monthStart, monthEnd))
                .writer(writer)
                .listener(writer)
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    private JdbcCursorItemReader<AggregatedScoreRow> monthlyScoreReader(LocalDate monthStart, LocalDate monthEnd) {
        return new JdbcCursorItemReaderBuilder<AggregatedScoreRow>()
                .name("monthlyScoreReader")
                .dataSource(dataSource)
                .sql(READER_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, monthStart);
                    ps.setObject(2, monthEnd);
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
