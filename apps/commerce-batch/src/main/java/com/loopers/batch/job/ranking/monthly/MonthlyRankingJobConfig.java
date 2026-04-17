package com.loopers.batch.job.ranking.monthly;

import com.loopers.batch.job.ranking.monthly.step.MonthlyRankingProcessor;
import com.loopers.batch.job.ranking.monthly.step.TruncateMonthlyMvTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankMonthly;
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
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

// spring.batch.job.name=monthlyRankingJob 일 때만 활성화
// Job 흐름: truncateMonthlyMvStep → monthlyAggregateAndRankStep
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_TRUNCATE = "truncateMonthlyMvStep";
    private static final String STEP_AGGREGATE_AND_RANK = "monthlyAggregateAndRankStep";
    private static final int CHUNK_SIZE = 10;

    // score = like * 0.2 + view * 0.1 + 0.7 * LOG(1 + sales)
    // 파라미터 1: 집계 시작(inclusive), 파라미터 2: 집계 종료(exclusive)
    private static final String MONTHLY_METRICS_SQL = """
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
    private final TruncateMonthlyMvTasklet truncateMonthlyMvTasklet;
    private final MonthlyRankingProcessor monthlyRankingProcessor;
    private final DataSource dataSource;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(truncateMonthlyMvStep())
                .next(monthlyAggregateAndRankStep())
                .listener(jobListener)
                .build();
    }

    // Step 1: 이전 집계 결과 전체 삭제 (재실행 시 중복 방지)
    @JobScope
    @Bean(STEP_TRUNCATE)
    public Step truncateMonthlyMvStep() {
        return new StepBuilder(STEP_TRUNCATE, jobRepository)
                .tasklet(truncateMonthlyMvTasklet, new ResourcelessTransactionManager())
                .listener(stepMonitorListener)
                .build();
    }

    // Step 2: product_metrics 집계 → 상위 100개 랭킹 산출 → mv_product_rank_monthly 저장
    // monthlyMetricsItemReader(null): @Configuration CGLIB 가 메서드 호출을 가로채 @StepScope 프록시를 반환
    @JobScope
    @Bean(STEP_AGGREGATE_AND_RANK)
    public Step monthlyAggregateAndRankStep() {
        return new StepBuilder(STEP_AGGREGATE_AND_RANK, jobRepository)
                .<ProductAggregation, MvProductRankMonthly>chunk(CHUNK_SIZE, new DataSourceTransactionManager(dataSource))
                .reader(monthlyMetricsItemReader(null))
                .processor(monthlyRankingProcessor)
                .writer(monthlyRankingItemWriter())
                .listener(stepMonitorListener)
                .build();
    }

    // targetYearMonth job 파라미터 늦은 바인딩 (yyyyMM 형식) — 집계 범위: [해당 월 1일, 다음 달 1일)
    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductAggregation> monthlyMetricsItemReader(
            @Value("#{jobParameters['targetYearMonth']}") String targetYearMonth
    ) {
        YearMonth yearMonth = YearMonth.parse(targetYearMonth, DateTimeFormatter.ofPattern("yyyyMM"));
        LocalDateTime startDateTime = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDateTime = yearMonth.plusMonths(1).atDay(1).atStartOfDay();

        return new JdbcCursorItemReaderBuilder<ProductAggregation>()
                .name("monthlyMetricsItemReader")
                .dataSource(dataSource)
                .sql(MONTHLY_METRICS_SQL)
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
    public JdbcBatchItemWriter<MvProductRankMonthly> monthlyRankingItemWriter() {
        return new JdbcBatchItemWriterBuilder<MvProductRankMonthly>()
                .dataSource(dataSource)
                .sql("""
                        INSERT INTO mv_product_rank_monthly
                          (product_id, rank, score, total_like, total_order, total_view, total_sales, base_date, created_at)
                        VALUES (:productId, :rank, :score, :totalLike, :totalOrder, :totalView, :totalSales, :baseDate, NOW())
                        """)
                .beanMapped()
                .build();
    }
}
