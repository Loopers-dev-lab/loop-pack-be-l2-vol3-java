package com.loopers.batch.job.ranking;

import com.loopers.batch.listener.JobListener;
import com.loopers.batch.listener.StepMonitorListener;
import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
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

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Configuration
public class MonthlyRankingJobConfig {

    public static final String JOB_NAME = "monthlyRankingJob";
    private static final String STEP_NAME = "monthlyRankingStep";
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
    private final MvProductRankMonthlyJpaRepository mvRepository;

    @Bean(JOB_NAME)
    public Job monthlyRankingJob() {
        return new JobBuilder(JOB_NAME, jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(monthlyRankingStep())
                .listener(jobListener)
                .build();
    }

    @JobScope
    @Bean(STEP_NAME)
    public Step monthlyRankingStep() {
        return new StepBuilder(STEP_NAME, jobRepository)
                .<ProductMetrics, RankedProductScore>chunk(CHUNK_SIZE, transactionManager)
                .reader(monthlyProductMetricsReader())
                .processor(monthlyRankingScoreProcessor())
                .writer(monthlyRankingWriter(null))
                .listener(stepMonitorListener)
                .build();
    }

    @StepScope
    @Bean
    public JpaPagingItemReader<ProductMetrics> monthlyProductMetricsReader() {
        return new JpaPagingItemReaderBuilder<ProductMetrics>()
                .name("monthlyProductMetricsReader")
                .entityManagerFactory(entityManagerFactory)
                .queryString("SELECT pm FROM ProductMetrics pm ORDER BY pm.id ASC")
                .pageSize(CHUNK_SIZE)
                .build();
    }

    @StepScope
    @Bean
    public ItemProcessor<ProductMetrics, RankedProductScore> monthlyRankingScoreProcessor() {
        return metrics -> {
            double score = metrics.getViewCount() * WEIGHT_VIEW
                    + metrics.getLikesCount() * WEIGHT_LIKE
                    + metrics.getOrderCount() * WEIGHT_ORDER;
            return new RankedProductScore(metrics.getProductId(), score);
        };
    }

    @StepScope
    @Bean
    public ItemWriter<RankedProductScore> monthlyRankingWriter(
            @Value("#{jobParameters['requestDate']}") String requestDate
    ) {
        return items -> {
            LocalDate date = LocalDate.parse(requestDate, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            YearMonth yearMonth = YearMonth.from(date);
            LocalDate periodStart = yearMonth.atDay(1);
            LocalDate periodEnd = yearMonth.atEndOfMonth();

            List<RankedProductScore> allScores = new ArrayList<>(items.getItems());
            allScores.sort(Comparator.comparingDouble(RankedProductScore::score).reversed());

            List<RankedProductScore> topN = allScores.stream().limit(TOP_N).toList();

            mvRepository.deleteByPeriodStartAndPeriodEnd(periodStart, periodEnd);

            int rank = 1;
            for (RankedProductScore scored : topN) {
                mvRepository.save(new MvProductRankMonthly(
                        scored.productId(), rank++, scored.score(), periodStart, periodEnd
                ));
            }

            log.info("월간 랭킹 저장 완료: period={} ~ {}, count={}", periodStart, periodEnd, topN.size());
        };
    }
}
