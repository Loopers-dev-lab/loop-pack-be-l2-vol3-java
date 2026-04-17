package com.loopers.batch.job.ranking;

import com.loopers.batch.infrastructure.entity.ProductMetricsEntity;
import com.loopers.batch.infrastructure.entity.ProductRankMonthlyEntity;
import com.loopers.batch.infrastructure.repository.ProductRankMonthlyJpaRepository;
import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.JobScope;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.loopers.batch.support.RankingPeriodKeyResolver;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_CLEANUP = "monthlyRankingCleanupStep";
    private static final String STEP_AGGREGATE = "monthlyRankingAggregateStep";
    private static final int CHUNK_SIZE = 1000;
    private static final int TOP_RANK_LIMIT = 100;

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final EntityManagerFactory entityManagerFactory;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final ProductRankMonthlyJpaRepository monthlyRepository;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyCleanupStep())
                .next(monthlyAggregateStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_CLEANUP)
    public Step monthlyCleanupStep() {
        return new StepBuilder(STEP_CLEANUP, jobRepository)
                .tasklet(monthlyCleanupTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public Tasklet monthlyCleanupTasklet(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return (contribution, chunkContext) -> {
            String yearMonth = resolveYearMonth(requestDate);
            log.info("월간 랭킹 기존 데이터 삭제: yearMonth={}", yearMonth);
            monthlyRepository.deleteByYearMonth(yearMonth);
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step monthlyAggregateStep() {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductMetricsEntity, ProductRankMonthlyEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyMetricsReader())
                .processor(monthlyRankingProcessor(null))
                .writer(monthlyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JpaPagingItemReader<ProductMetricsEntity> monthlyMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetricsEntity>()
                .name("monthlyMetricsReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString(
                        "SELECT m FROM ProductMetricsEntity m " +
                        "ORDER BY (m.viewCount * " + WEIGHT_VIEW +
                        " + m.likeCount * " + WEIGHT_LIKE +
                        " + m.orderCount * " + WEIGHT_ORDER + ") DESC"
                )
                .pageSize(TOP_RANK_LIMIT)
                .maxItemCount(TOP_RANK_LIMIT)
                .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMetricsEntity, ProductRankMonthlyEntity> monthlyRankingProcessor(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        String yearMonth = resolveYearMonth(requestDate);

        return metrics -> {
            int rank = rankCounter.incrementAndGet();
            double score = metrics.getViewCount() * WEIGHT_VIEW
                    + metrics.getLikeCount() * WEIGHT_LIKE
                    + metrics.getOrderCount() * WEIGHT_ORDER;

            return new ProductRankMonthlyEntity(
                    metrics.getProductId(),
                    yearMonth,
                    metrics.getLikeCount(),
                    metrics.getOrderCount(),
                    metrics.getViewCount(),
                    metrics.getTotalSalesAmount(),
                    score,
                    rank
            );
        };
    }

    @StepScope
    @Bean
    public ItemWriter<ProductRankMonthlyEntity> monthlyRankingWriter(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return chunk -> {
            List<? extends ProductRankMonthlyEntity> items = chunk.getItems();
            monthlyRepository.saveAll(items);
            log.info("월간 랭킹 {} 건 저장 완료", items.size());
        };
    }

    private String resolveYearMonth(String requestDate) {
        LocalDate date = RankingPeriodKeyResolver.parseDate(requestDate);
        return RankingPeriodKeyResolver.toYearMonth(date);
    }
}
