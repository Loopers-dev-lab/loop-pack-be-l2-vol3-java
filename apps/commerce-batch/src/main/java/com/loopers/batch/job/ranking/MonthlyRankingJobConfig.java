package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.MonthlyRankingCleanupTasklet;
import com.loopers.batch.job.ranking.step.MonthlyRankingProcessor;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankMonthly;
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
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEANUP = "monthlyRankingCleanupStep";
    private static final String STEP_AGGREGATE = "monthlyRankingAggregateStep";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final MonthlyRankingCleanupTasklet cleanupTasklet;
    private final MonthlyRankingProcessor processor;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
            .incrementer(new RunIdIncrementer())
            .start(cleanupStep())
            .next(aggregateStep())
            .listener(jobListener)
            .build();
    }

    @JobScope
    @Bean(STEP_CLEANUP)
    public Step cleanupStep() {
        return new StepBuilder(STEP_CLEANUP, jobRepository)
            .tasklet(cleanupTasklet, transactionManager)
            .listener(stepMonitorListener)
            .build();
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step aggregateStep() {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
            .<ProductScoreRow, MvProductRankMonthly>chunk(CHUNK_SIZE, transactionManager)
            .reader(monthlyReader(null))
            .processor(processor)
            .writer(monthlyWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductScoreRow> monthlyReader(
        @Value("#{jobParameters['monthStartDate']}") String monthStartDate
    ) {
        LocalDate start = LocalDate.parse(monthStartDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate end = YearMonth.from(start).atEndOfMonth();

        return new JdbcCursorItemReaderBuilder<ProductScoreRow>()
            .name("monthlyRankingReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(view_count) AS view_count,
                       SUM(like_count) AS like_count,
                       SUM(order_count) AS order_count,
                       (SUM(view_count) * 1 + SUM(like_count) * 2 + SUM(order_count) * 7) AS total_score
                FROM product_metrics_daily
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY product_id
                ORDER BY total_score DESC
                LIMIT 100
                """)
            .preparedStatementSetter(ps -> {
                ps.setObject(1, start);
                ps.setObject(2, end);
            })
            .beanRowMapper(ProductScoreRow.class)
            .build();
    }

    @StepScope
    @Bean
    public JpaItemWriter<MvProductRankMonthly> monthlyWriter() {
        return new JpaItemWriterBuilder<MvProductRankMonthly>()
            .entityManagerFactory(entityManagerFactory)
            .build();
    }
}
