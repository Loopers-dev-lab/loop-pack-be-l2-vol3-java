package com.loopers.batch.job.ranking.monthly;

import com.loopers.batch.job.ranking.ProductMetricsRow;
import com.loopers.batch.job.ranking.monthly.step.ClearMonthlyRankingTasklet;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankMonthlyModel;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicInteger;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEAR = "clearMonthlyRankingStep";
    private static final String STEP_AGGREGATE = "aggregateMonthlyRankingStep";
    private static final int CHUNK_SIZE = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ClearMonthlyRankingTasklet clearMonthlyRankingTasklet;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(clearMonthlyRankingStep())
                .next(aggregateMonthlyRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_CLEAR)
    public Step clearMonthlyRankingStep() {
        return new StepBuilder(STEP_CLEAR, jobRepository)
                .tasklet(clearMonthlyRankingTasklet, transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step aggregateMonthlyRankingStep() {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductMetricsRow, MvProductRankMonthlyModel>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyRankingReader(null))
                .processor(monthlyRankingProcessor(null))
                .writer(monthlyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    // Reader — 주간과 동일한 SQL (product_metrics에서 TOP 100)
    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductMetricsRow> monthlyRankingReader(DataSource dataSource) {
        return new JdbcCursorItemReaderBuilder<ProductMetricsRow>()
                .name("monthlyRankingReader")
                .dataSource(dataSource)
                .sql("""
                    SELECT pm.product_id,
                           pm.view_count,
                           pm.like_count,
                           pm.sales_count,
                           (pm.view_count * 0.1 + pm.like_count * 0.2 + pm.sales_count * 0.7) AS score
                    FROM product_metrics pm
                    ORDER BY score DESC
                    LIMIT 100
                    """)
                .rowMapper((rs, rowNum) -> new ProductMetricsRow(
                        rs.getLong("product_id"),
                        rs.getLong("view_count"),
                        rs.getLong("like_count"),
                        rs.getLong("sales_count"),
                        rs.getDouble("score")))
                .build();
    }

    // Processor — yearMonth 계산 + 순위 부여 (차이점: yearWeek 대신 yearMonth)
    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsRow, MvProductRankMonthlyModel> monthlyRankingProcessor(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        String yearMonth = ClearMonthlyRankingTasklet.computeYearMonth(requestDate);

        return item -> new MvProductRankMonthlyModel(
                item.productId(),
                item.viewCount(),
                item.likeCount(),
                item.salesCount(),
                item.score(),
                rankCounter.incrementAndGet(),
                yearMonth
        );
    }

    @StepScope
    @Bean
    public JpaItemWriter<MvProductRankMonthlyModel> monthlyRankingWriter(
            EntityManagerFactory entityManagerFactory
    ) {
        return new JpaItemWriterBuilder<MvProductRankMonthlyModel>()
                .entityManagerFactory(entityManagerFactory)
                .build();
    }
}
