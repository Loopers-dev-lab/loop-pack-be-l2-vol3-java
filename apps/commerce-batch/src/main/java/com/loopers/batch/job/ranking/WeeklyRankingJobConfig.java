package com.loopers.batch.job.ranking;

import com.loopers.batch.infrastructure.entity.ProductMetricsEntity;
import com.loopers.batch.infrastructure.entity.ProductRankWeeklyEntity;
import com.loopers.batch.infrastructure.repository.ProductRankWeeklyJpaRepository;
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
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_CLEANUP = "weeklyRankingCleanupStep";
    private static final String STEP_AGGREGATE = "weeklyRankingAggregateStep";
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
    private final ProductRankWeeklyJpaRepository weeklyRepository;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyCleanupStep())
                .next(weeklyAggregateStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_CLEANUP)
    public Step weeklyCleanupStep() {
        return new StepBuilder(STEP_CLEANUP, jobRepository)
                .tasklet(weeklyCleanupTasklet(null), transactionManager)
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public Tasklet weeklyCleanupTasklet(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return (contribution, chunkContext) -> {
            String yearWeek = resolveYearWeek(requestDate);
            log.info("주간 랭킹 기존 데이터 삭제: yearWeek={}", yearWeek);
            weeklyRepository.deleteByYearWeek(yearWeek);
            return RepeatStatus.FINISHED;
        };
    }

    @JobScope
    @Bean(STEP_AGGREGATE)
    public Step weeklyAggregateStep() {
        return new StepBuilder(STEP_AGGREGATE, jobRepository)
                .<ProductMetricsEntity, ProductRankWeeklyEntity>chunk(CHUNK_SIZE, transactionManager)
                .reader(weeklyMetricsReader())
                .processor(weeklyRankingProcessor(null))
                .writer(weeklyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JpaPagingItemReader<ProductMetricsEntity> weeklyMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetricsEntity>()
                .name("weeklyMetricsReader")
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
    public ItemProcessor<ProductMetricsEntity, ProductRankWeeklyEntity> weeklyRankingProcessor(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        AtomicInteger rankCounter = new AtomicInteger(0);
        String yearWeek = resolveYearWeek(requestDate);

        return metrics -> {
            int rank = rankCounter.incrementAndGet();
            double score = metrics.getViewCount() * WEIGHT_VIEW
                    + metrics.getLikeCount() * WEIGHT_LIKE
                    + metrics.getOrderCount() * WEIGHT_ORDER;

            return new ProductRankWeeklyEntity(
                    metrics.getProductId(),
                    yearWeek,
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
    public ItemWriter<ProductRankWeeklyEntity> weeklyRankingWriter(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return chunk -> {
            List<? extends ProductRankWeeklyEntity> items = chunk.getItems();
            weeklyRepository.saveAll(items);
            log.info("주간 랭킹 {} 건 저장 완료", items.size());
        };
    }

    private String resolveYearWeek(String requestDate) {
        LocalDate date = RankingPeriodKeyResolver.parseDate(requestDate);
        return RankingPeriodKeyResolver.toYearWeek(date);
    }
}
