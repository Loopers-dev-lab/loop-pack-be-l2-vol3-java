package com.loopers.batch.job.ranking.weekly;

import com.loopers.batch.job.ranking.weekly.step.TruncateWeeklyMvTasklet;
import com.loopers.batch.job.ranking.weekly.step.WeeklyRankingProcessor;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductAggregation;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;

// spring.batch.job.name=weeklyRankingJob 일 때만 활성화
// Job 흐름: truncateWeeklyMvStep → weeklyAggregateAndRankStep
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_TRUNCATE = "truncateWeeklyMvStep";
    private static final String STEP_AGGREGATE_AND_RANK = "weeklyAggregateAndRankStep";
    private static final int CHUNK_SIZE = 10;

    // score = like * 0.2 + view * 0.1 + 0.7 * LOG(1 + sales)
    // 파라미터 1: 집계 시작(inclusive), 파라미터 2: 집계 종료(exclusive)
    private static final String WEEKLY_METRICS_SQL = """
            SELECT product_id,
                   SUM(like_count) * 0.2 + SUM(view_count) * 0.1
                       + 0.7 * LOG(1 + SUM(sales_amount)) AS score,
                   SUM(like_count)   AS total_like,
                   SUM(order_count)  AS total_order,
                   SUM(view_count)   AS total_view,
                   SUM(sales_amount) AS total_sales
            FROM product_metrics
            WHERE metric_hour >= ?
              AND metric_hour <  ?
              AND deleted_at IS NULL
            GROUP BY product_id
            ORDER BY score DESC
            LIMIT 100
            """;

    private final JobRepository jobRepository;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final TruncateWeeklyMvTasklet truncateWeeklyMvTasklet;
    private final WeeklyRankingProcessor weeklyRankingProcessor;
    private final DataSource dataSource;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(truncateWeeklyMvStep())
                .next(weeklyAggregateAndRankStep())
                .listener(jobListener)
                .build();
    }

    // Step 1: 이전 집계 결과 전체 삭제 (재실행 시 중복 방지)
    @JobScope
    @Bean(STEP_TRUNCATE)
    public Step truncateWeeklyMvStep() {
        return new StepBuilder(STEP_TRUNCATE, jobRepository)
                .tasklet(truncateWeeklyMvTasklet, new ResourcelessTransactionManager())
                .listener(stepMonitorListener)
                .build();
    }

    // Step 2: product_metrics 집계 → 상위 100개 랭킹 산출 → mv_product_rank_weekly 저장
    // weeklyMetricsItemReader(null): @Configuration CGLIB 가 메서드 호출을 가로채 @StepScope 프록시를 반환
    @JobScope
    @Bean(STEP_AGGREGATE_AND_RANK)
    public Step weeklyAggregateAndRankStep() {
        return new StepBuilder(STEP_AGGREGATE_AND_RANK, jobRepository)
                .<ProductAggregation, MvProductRankWeekly>chunk(CHUNK_SIZE, new DataSourceTransactionManager(dataSource))
                .reader(weeklyMetricsItemReader(null))
                .processor(weeklyRankingProcessor)
                .writer(weeklyRankingItemWriter())
                .listener(stepMonitorListener)
                .build();
    }

    // targetDate job 파라미터 늦은 바인딩 — targetDate 는 해당 주 월요일, 집계 범위: [월요일, 월요일+7일)
    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductAggregation> weeklyMetricsItemReader(
            @Value("#{jobParameters['targetDate']}") LocalDate targetDate
    ) {
        if (targetDate.getDayOfWeek() != java.time.DayOfWeek.MONDAY) {
            throw new IllegalArgumentException("targetDate 는 월요일이어야 합니다: " + targetDate);
        }
        LocalDateTime startDateTime = targetDate.atStartOfDay();
        LocalDateTime endDateTime = targetDate.plusDays(7).atStartOfDay();

        return new JdbcCursorItemReaderBuilder<ProductAggregation>()
                .name("weeklyMetricsItemReader")
                .dataSource(dataSource)
                .sql(WEEKLY_METRICS_SQL)
                .preparedStatementSetter(ps -> {
                    ps.setObject(1, startDateTime);
                    ps.setObject(2, endDateTime);
                })
                .rowMapper((rs, rowNum) -> new ProductAggregation(
                        rs.getLong("product_id"),
                        rs.getDouble("score"),
                        rs.getLong("total_like"),
                        rs.getLong("total_order"),
                        rs.getLong("total_view"),
                        rs.getLong("total_sales")
                ))
                .build();
    }

    @Bean
    public JdbcBatchItemWriter<MvProductRankWeekly> weeklyRankingItemWriter() {
        return new JdbcBatchItemWriterBuilder<MvProductRankWeekly>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO mv_product_rank_weekly
                          (product_id, rank, score, total_like, total_order, total_view, total_sales, base_date, created_at)
                        VALUES (:productId, :rank, :score, :totalLike, :totalOrder, :totalView, :totalSales, :baseDate, NOW())
                        """)
                .beanMapped()
                .build();
    }
}
