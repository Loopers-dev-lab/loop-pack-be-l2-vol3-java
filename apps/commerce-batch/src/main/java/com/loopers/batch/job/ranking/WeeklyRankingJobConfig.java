package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_TRUNCATE = "weeklyTruncateStep";
    private static final String STEP_AGGREGATE = "weeklyAggregateStep";
    private static final int CHUNK_SIZE = 1000;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final DataSource dataSource;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(weeklyTruncateStep(null))
            .next(weeklyAggregateStep(null))
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_TRUNCATE)
    public Step weeklyTruncateStep(@Value("#{jobParameters['targetDate']}") String targetDate) {
        Tasklet tasklet = (contribution, chunkContext) -> {
            new org.springframework.jdbc.core.JdbcTemplate(dataSource)
                .update("DELETE FROM mv_product_rank_weekly");
            return RepeatStatus.FINISHED;
        };
        return new StepBuilder(STEP_TRUNCATE, jobRepository)
            .tasklet(tasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step weeklyAggregateStep(@Value("#{jobParameters['targetDate']}") String targetDate) {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
            .<ProductMetricsAggregateRow, MvRankRow>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyReader(targetDate))
            .processor(weeklyProcessor(targetDate))
            .writer(weeklyWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @StepScope
    @Bean("weeklyReader")
    public JdbcCursorItemReader<ProductMetricsAggregateRow> weeklyReader(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, DATE_FORMATTER);
        LocalDate startDate = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate endDate = startDate.plusDays(6);

        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregateRow>()
            .name("weeklyReader")
            .dataSource(dataSource)
            .sql(
                "SELECT product_id, SUM(like_count) AS like_count, SUM(order_count) AS order_count, " +
                "(SUM(like_count) * " + RankingScoreCalculator.LIKE_WEIGHT + " + SUM(order_count) * " + RankingScoreCalculator.ORDER_WEIGHT + ") AS score " +
                "FROM product_metrics " +
                "WHERE date BETWEEN ? AND ? " +
                "GROUP BY product_id " +
                "ORDER BY score DESC " +
                "LIMIT 100"
            )
            .preparedStatementSetter(ps -> {
                ps.setObject(1, startDate);
                ps.setObject(2, endDate);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregateRow(
                rs.getLong("product_id"),
                rs.getInt("like_count"),
                rs.getInt("order_count"),
                rs.getDouble("score")
            ))
            .build();
    }

    @StepScope
    @Bean("weeklyProcessor")
    public ItemProcessor<ProductMetricsAggregateRow, MvRankRow> weeklyProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDate
    ) {
        LocalDate target = LocalDate.parse(targetDate, DATE_FORMATTER);
        LocalDate monday = target.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        String yearMonthWeek = monday.getYear() + "-W" + String.format("%02d", monday.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear()));

        return row -> new MvRankRow(
            row.productId(),
            row.likeCount(),
            row.orderCount(),
            row.score(),
            yearMonthWeek
        );
    }

    @Bean("weeklyWriter")
    public JdbcBatchItemWriter<MvRankRow> weeklyWriter() {
        return new JdbcBatchItemWriterBuilder<MvRankRow>()
            .dataSource(dataSource)
            .sql(
                "INSERT INTO mv_product_rank_weekly " +
                "(product_id, like_count, order_count, score, year_month_week, updated_at) " +
                "VALUES (:productId, :likeCount, :orderCount, :score, :period, NOW())"
            )
            .beanMapped()
            .build();
    }
}
