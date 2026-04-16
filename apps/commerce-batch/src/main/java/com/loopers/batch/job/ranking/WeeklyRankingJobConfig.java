package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
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
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class WeeklyRankingJobConfig {

    public static final String JOB_NAME = "weeklyRankingJob";
    private static final String STEP_NAME = "weeklyRankingStep";
    private static final int CHUNK_SIZE = 100;
    private static final int TOP_N = 100;

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final JobListener jobListener;
    private final StepMonitorListener stepMonitorListener;
    private final EntityManagerFactory entityManagerFactory;
    private final MvProductRankWeeklyJpaRepository mvRepository;

    @Bean(JOB_NAME)
    public Job weeklyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(weeklyRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step weeklyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductMetrics, RankedProductScore>chunk(CHUNK_SIZE, transactionManager)
                .reader(productMetricsReader())
                .processor(rankingScoreProcessor())
                .writer(weeklyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JpaPagingItemReader<ProductMetrics> productMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetrics>()
                .name("productMetricsReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT pm FROM ProductMetrics pm ORDER BY pm.id ASC")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMetrics, RankedProductScore> rankingScoreProcessor() {
        return metrics -> {
            double score = metrics.getViewCount() * WEIGHT_VIEW
                    + metrics.getLikesCount() * WEIGHT_LIKE
                    + metrics.getOrderCount() * WEIGHT_ORDER;
            return new RankedProductScore(metrics.getProductId(), score);
        };
    }

    @StepScope
    @Bean
    public ItemWriter<RankedProductScore> weeklyRankingWriter(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return items -> {
            LocalDate date = LocalDate.parse(requestDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            LocalDate periodStart = date.with(DayOfWeek.MONDAY);
            LocalDate periodEnd = date.with(DayOfWeek.SUNDAY);

            // 모든 chunk의 데이터를 모아서 정렬 후 Top N 저장
            List<RankedProductScore> allScores = new ArrayList<>(items.getItems());
            allScores.sort(Comparator.comparingDouble(RankedProductScore::score).reversed());

            List<RankedProductScore> topN = allScores.stream().limit(TOP_N).toList();

            // 기존 데이터 삭제
            mvRepository.deleteByPeriodStartAndPeriodEnd(periodStart, periodEnd);

            // Top N 저장
            int rank = 1;
            for (RankedProductScore scored : topN) {
                mvRepository.save(new MvProductRankWeekly(
                        scored.productId(), rank++, scored.score(), periodStart, periodEnd
                ));
            }

            log.info("주간 랭킹 저장 완료: period={} ~ {}, count={}", periodStart, periodEnd, topN.size());
        };
    }
}
