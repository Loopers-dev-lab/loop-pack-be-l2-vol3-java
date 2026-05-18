package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.ProductMetricsAggregation;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = RankingAggregationJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class RankingAggregationJobConfig {

    public static final String JOB_NAME = "rankingAggregationJob";
    private static final int CHUNK_SIZE = 100;
    private static final int TOP_N = 100;

    // saturation 상수: ProductMetricsAggregation.SATURATION_K(=100)와 일치해야 한다.
    // TODO: 실제 운영 데이터로 튜닝 필요. 현재는 임의값.
    private static final double SATURATION_K = 100.0;

    private static final String AGGREGATION_SQL = """
        SELECT pm.product_id,
               SUM(pm.view_count) AS view_count,
               SUM(pm.like_count) AS like_count,
               SUM(pm.sales_count) AS sales_count
        FROM product_metrics pm
        INNER JOIN products p ON pm.product_id = p.id AND p.deleted_at IS NULL
        WHERE pm.metric_date BETWEEN ? AND ?
        GROUP BY pm.product_id
        ORDER BY (
            GREATEST(SUM(pm.view_count), 0)  / (GREATEST(SUM(pm.view_count), 0)  + %1$s) * 0.1 +
            GREATEST(SUM(pm.like_count), 0)  / (GREATEST(SUM(pm.like_count), 0)  + %1$s) * 0.2 +
            GREATEST(SUM(pm.sales_count), 0) / (GREATEST(SUM(pm.sales_count), 0) + %1$s) * 0.7
        ) DESC
        LIMIT %2$s
        """.formatted(SATURATION_K, TOP_N);

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final MvProductRankWeeklyJpaRepository weeklyRepository;
    private final MvProductRankMonthlyJpaRepository monthlyRepository;
    private final DataSource dataSource;

    @Bean(JOB_NAME)
    public Job rankingAggregationJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .start(weeklyRankingStep())
            .next(monthlyRankingStep())
            .listener(jobListener)
            .build();
    }

    // === Weekly ===

    @Bean
    public Step weeklyRankingStep() {
        return new StepBuilder("weeklyRankingStep", jobRepository)
            .<ProductMetricsAggregation, MvProductRankWeekly>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyMetricsReader(null))
            .processor(weeklyProcessor(null))
            .writer(weeklyWriter(null))
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ProductMetricsAggregation> weeklyMetricsReader(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate 파라미터가 필요합니다");
        }

        LocalDate fromDate = baseDate.minusDays(6);
        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregation>()
            .name("weeklyMetricsReader")
            .dataSource(dataSource)
            .sql(AGGREGATION_SQL)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, fromDate);
                ps.setObject(2, baseDate);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregation(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count")
            ))
            .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<ProductMetricsAggregation, MvProductRankWeekly> weeklyProcessor(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        AtomicInteger rankCounter = new AtomicInteger(1);
        return item -> MvProductRankWeekly.of(item, rankCounter.getAndIncrement(), baseDate);
    }

    @Bean
    @StepScope
    public ItemWriter<MvProductRankWeekly> weeklyWriter(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        return chunk -> {
            weeklyRepository.deleteByAggregatedAt(baseDate);
            weeklyRepository.saveAll(chunk.getItems());
        };
    }

    // === Monthly ===

    @Bean
    public Step monthlyRankingStep() {
        return new StepBuilder("monthlyRankingStep", jobRepository)
            .<ProductMetricsAggregation, MvProductRankMonthly>chunk(CHUNK_SIZE, transactionManager)
            .reader(monthlyMetricsReader(null))
            .processor(monthlyProcessor(null))
            .writer(monthlyWriter(null))
            .listener(stepMonitorListener)
            .build();
    }

    @Bean
    @StepScope
    public JdbcCursorItemReader<ProductMetricsAggregation> monthlyMetricsReader(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        if (baseDate == null) {
            throw new IllegalArgumentException("baseDate 파라미터가 필요합니다");
        }

        LocalDate fromDate = baseDate.minusDays(29);
        return new JdbcCursorItemReaderBuilder<ProductMetricsAggregation>()
            .name("monthlyMetricsReader")
            .dataSource(dataSource)
            .sql(AGGREGATION_SQL)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, fromDate);
                ps.setObject(2, baseDate);
            })
            .rowMapper((rs, rowNum) -> new ProductMetricsAggregation(
                rs.getLong("product_id"),
                rs.getLong("view_count"),
                rs.getLong("like_count"),
                rs.getLong("sales_count")
            ))
            .build();
    }

    @Bean
    @StepScope
    public ItemProcessor<ProductMetricsAggregation, MvProductRankMonthly> monthlyProcessor(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        AtomicInteger rankCounter = new AtomicInteger(1);
        return item -> MvProductRankMonthly.of(item, rankCounter.getAndIncrement(), baseDate);
    }

    @Bean
    @StepScope
    public ItemWriter<MvProductRankMonthly> monthlyWriter(
        @Value("#{jobParameters['baseDate']}") LocalDate baseDate) {

        return chunk -> {
            monthlyRepository.deleteByAggregatedAt(baseDate);
            monthlyRepository.saveAll(chunk.getItems());
        };
    }
}
