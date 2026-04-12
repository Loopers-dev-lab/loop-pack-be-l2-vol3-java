package com.loopers.batch.job.rank;

import com.loopers.batch.job.rank.step.AggregatedScoreRow;
import com.loopers.batch.job.rank.step.CleanupMvTasklet;
import com.loopers.batch.job.rank.step.RankAssignProcessor;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.rank.MvProductRankRepository;
import com.loopers.domain.rank.MvProductRankRow;
import com.loopers.domain.rank.RankPeriodType;
import com.loopers.domain.rank.RankingKeyGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankJobConfig {

    public static final String JOB_NAME = "weeklyRankJob";
    private static final String CLEANUP_STEP = "cleanupWeeklyStep";
    private static final String BUILD_STEP = "buildWeeklyRankStep";
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
    public Job weeklyRankJob(Step cleanupWeeklyStep, Step buildWeeklyRankStep) {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .listener(jobListener)
                .start(cleanupWeeklyStep)
                .next(buildWeeklyRankStep)
                .build();
    }

    @Bean(CLEANUP_STEP)
    @JobScope
    public Step cleanupWeeklyStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        String periodKey = RankingKeyGenerator.weeklyPeriodKey(date);
        return new StepBuilder(CLEANUP_STEP, jobRepository)
                .tasklet(new CleanupMvTasklet(mvProductRankRepository, RankPeriodType.WEEKLY, periodKey), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @Bean(BUILD_STEP)
    @JobScope
    public Step buildWeeklyRankStep(@Value("#{jobParameters['date']}") String dateStr) {
        LocalDate date = LocalDate.parse(dateStr, DATE_FMT);
        String periodKey = RankingKeyGenerator.weeklyPeriodKey(date);
        LocalDate weekStart = RankingKeyGenerator.weekStart(date);
        LocalDate weekEnd = RankingKeyGenerator.weekEnd(date);

        return new StepBuilder(BUILD_STEP, jobRepository)
                .<AggregatedScoreRow, MvProductRankRow>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyScoreReader(weekStart, weekEnd))
                .processor(new RankAssignProcessor(periodKey))
                .writer(rankWriter())
                .listener(stepMonitorListener)
                .listener(chunkListener)
                .build();
    }

    private JdbcCursorItemReader<AggregatedScoreRow> weeklyScoreReader(LocalDate weekStart, LocalDate weekEnd) {
        return new JdbcCursorItemReaderBuilder<AggregatedScoreRow>()
                .name("weeklyScoreReader")
                .dataSource(dataSource)
                .sql(READER_SQL)
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

    private JdbcBatchItemWriter<MvProductRankRow> rankWriter() {
        return new JdbcBatchItemWriterBuilder<MvProductRankRow>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO mv_product_rank_weekly
                            (period_key, rank_no, ref_product_id, score, view_count, like_count, order_amount, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, NOW(), NOW())
                        """)
                .itemPreparedStatementSetter((row, ps) -> {
                    ps.setString(1, row.periodKey());
                    ps.setInt(2, row.rankNo());
                    ps.setLong(3, row.refProductId());
                    ps.setDouble(4, row.score());
                    ps.setLong(5, row.viewCount());
                    ps.setLong(6, row.likeCount());
                    ps.setBigDecimal(7, row.orderAmount());
                })
                .build();
    }
}
