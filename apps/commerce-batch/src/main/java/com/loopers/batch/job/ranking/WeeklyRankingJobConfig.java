package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.ProductMetricsAggregation;
import com.loopers.domain.ranking.ProductRankMv;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.WeekFields;

@Slf4j
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final PlatformTransactionManager transactionManager;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .validator(new RankingJobParametersValidator())
            .start(weeklyRankingStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
            .<ProductMetricsAggregation, ProductRankMv>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyRankingReader(null, null))
            .processor(weeklyRankingProcessor(null))
            .writer(weeklyRankingWriter(null))
            .listener(stepMonitorListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricsAggregation> weeklyRankingReader(
        DataSource dataSource,
        @Value("#{jobParameters['targetDate']}") String targetDateStr
    ) {
        LocalDate targetDate = LocalDate.parse(targetDateStr);
        LocalDate weekStart = targetDate.with(DayOfWeek.MONDAY);
        LocalDate weekEnd = targetDate.with(DayOfWeek.SUNDAY);

        log.info("주간 랭킹 집계 기간: {} ~ {}", weekStart, weekEnd);

        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregation>()
            .name("weeklyRankingReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(like_count) AS like_count,
                       SUM(view_count) AS view_count,
                       SUM(sales_count) AS sales_count,
                       SUM(sales_amount) AS sales_amount,
                       ROW_NUMBER() OVER(
                           ORDER BY (SUM(view_count) * 0.1 + SUM(like_count) * 0.2 + SUM(sales_amount) * 0.6) DESC, product_id ASC
                       ) AS `rank`
                FROM product_metrics_daily
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY product_id
                ORDER BY `rank`
                LIMIT 100
                """)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, weekStart);
                ps.setObject(2, weekEnd);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregation(
                rs.getLong("product_id"),
                rs.getLong("like_count"),
                rs.getLong("view_count"),
                rs.getLong("sales_count"),
                rs.getLong("sales_amount"),
                rs.getInt("rank")
            ))
            .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsAggregation, ProductRankMv> weeklyRankingProcessor(
        @Value("#{jobParameters['targetDate']}") String targetDateStr
    ) {
        LocalDate targetDate = LocalDate.parse(targetDateStr);
        WeekFields weekFields = WeekFields.ISO;
        int weekNumber = targetDate.get(weekFields.weekOfWeekBasedYear());
        int year = targetDate.get(weekFields.weekBasedYear());
        String yearWeek = String.format("%d-W%02d", year, weekNumber);

        return aggregation -> ProductRankMv.from(aggregation, yearWeek);
    }

    @StepScope
    @Bean
    public JdbcBatchItemWriter<ProductRankMv> weeklyRankingWriter(DataSource dataSource) {
        return new JdbcBatchItemWriterBuilder<ProductRankMv>()
            .dataSource(dataSource)
            .sql("""
                INSERT INTO mv_product_rank_weekly
                    (product_id, year_week, like_count, view_count, sales_count, sales_amount, score, `rank`, created_at, updated_at)
                VALUES
                    (:productId, :periodKey, :likeCount, :viewCount, :salesCount, :salesAmount, :score, :rank, NOW(), NOW())
                ON DUPLICATE KEY UPDATE
                    like_count = VALUES(like_count),
                    view_count = VALUES(view_count),
                    sales_count = VALUES(sales_count),
                    sales_amount = VALUES(sales_amount),
                    score = VALUES(score),
                    `rank` = VALUES(`rank`),
                    updated_at = NOW()
                """)
            .beanMapped()
            .build();
    }
}
