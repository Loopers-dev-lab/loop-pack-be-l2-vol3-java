package com.loopers.batch.job.ranking;

import com.loopers.batch.job.ranking.step.WeeklyRankingCleanupTasklet;
import com.loopers.batch.job.ranking.step.WeeklyRankingProcessor;
import com.loopers.batch.listener.ChunkListener;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.domain.ranking.RankingScoreCalculator;
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
import java.time.format.DateTimeFormatter;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEANUP = "weeklyRankingCleanupStep";
    private static final String STEP_AGGREGATE = "weeklyRankingAggregateStep";
    private static final int CHUNK_SIZE = 500;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ChunkListener chunkListener;
    private final WeeklyRankingCleanupTasklet cleanupTasklet;
    private final WeeklyRankingProcessor processor;
    private final DataSource dataSource;
    private final EntityManagerFactory entityManagerFactory;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
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
            .<ProductScoreRow, MvProductRankWeekly>chunk(CHUNK_SIZE, transactionManager)
            .reader(weeklyReader(null))
            .processor(processor)
            .writer(weeklyWriter())
            .listener(stepMonitorListener)
            .listener(chunkListener)
            .build();
    }

    @StepScope
    @Bean
    public JdbcCursorItemReader<ProductScoreRow> weeklyReader(
        @Value("#{jobParameters['weekStartDate']}") String weekStartDate
    ) {
        LocalDate start = LocalDate.parse(weekStartDate, DateTimeFormatter.BASIC_ISO_DATE);
        LocalDate end = start.plusDays(6);

        return new JdbcCursorItemReaderBuilder<ProductScoreRow>()
            .name("weeklyRankingReader")
            .dataSource(dataSource)
            .sql("""
                SELECT product_id,
                       SUM(view_count) AS view_count,
                       SUM(like_count) AS like_count,
                       SUM(order_count) AS order_count,
                       (SUM(view_count) * %d + SUM(like_count) * %d + SUM(order_count) * %d) AS total_score
                FROM product_metrics_daily
                WHERE metric_date BETWEEN ? AND ?
                GROUP BY product_id
                ORDER BY total_score DESC
                LIMIT 100
                """.formatted(
                RankingScoreCalculator.VIEW_WEIGHT,
                RankingScoreCalculator.LIKE_WEIGHT,
                RankingScoreCalculator.ORDER_WEIGHT
            ))
            .preparedStatementSetter(ps -> {
                ps.setObject(1, start);
                ps.setObject(2, end);
            })
            .beanRowMapper(ProductScoreRow.class)
            .build();
    }

    @StepScope
    @Bean
    public JpaItemWriter<MvProductRankWeekly> weeklyWriter() {
        return new JpaItemWriterBuilder<MvProductRankWeekly>()
            .entityManagerFactory(entityManagerFactory)
            .build();
    }
}
