package com.loopers.infrastructure.batch;

import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.ranking.MvProductRankMonthly;
import com.loopers.domain.ranking.MvProductRankWeekly;
import com.loopers.infrastructure.metrics.ProductMetricsDailyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankMonthlyJpaRepository;
import com.loopers.infrastructure.ranking.MvProductRankWeeklyJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class RankingAggregationJobConfig {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int TOP_RANK_LIMIT = 100;

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;
    private final MvProductRankWeeklyJpaRepository mvProductRankWeeklyJpaRepository;
    private final MvProductRankMonthlyJpaRepository mvProductRankMonthlyJpaRepository;

    @Bean
    public Job rankingAggregationJob() {
        return new JobBuilder("rankingAggregationJob", jobRepository)
                .start(weeklyRankingStep())
                .next(monthlyRankingStep())
                .build();
    }

    @Bean
    public Step weeklyRankingStep() {
        return new StepBuilder("weeklyRankingStep", jobRepository)
                .tasklet(weeklyRankingTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Step monthlyRankingStep() {
        return new StepBuilder("monthlyRankingStep", jobRepository)
                .tasklet(monthlyRankingTasklet(), transactionManager)
                .build();
    }

    @Bean
    public Tasklet weeklyRankingTasklet() {
        return (contribution, chunkContext) -> {
            String targetDateStr = (String) chunkContext.getStepContext()
                    .getJobParameters().get("targetDate");
            LocalDate targetDate = LocalDate.parse(targetDateStr, DATE_FORMATTER);
            LocalDate periodStart = targetDate.minusDays(6);
            LocalDate periodEnd = targetDate;

            log.info("주간 랭킹 집계 시작: {} ~ {}", periodStart, periodEnd);

            List<ProductMetricsDaily> dailyList =
                    productMetricsDailyJpaRepository.findByMetricDateBetween(periodStart, periodEnd);

            List<MvProductRankWeekly> rankings = aggregateAndRankWeekly(dailyList, periodStart, periodEnd);

            mvProductRankWeeklyJpaRepository.deleteByPeriod(periodStart, periodEnd);
            mvProductRankWeeklyJpaRepository.saveAll(rankings);

            log.info("주간 랭킹 집계 완료: {} 건", rankings.size());
            return RepeatStatus.FINISHED;
        };
    }

    @Bean
    public Tasklet monthlyRankingTasklet() {
        return (contribution, chunkContext) -> {
            String targetDateStr = (String) chunkContext.getStepContext()
                    .getJobParameters().get("targetDate");
            LocalDate targetDate = LocalDate.parse(targetDateStr, DATE_FORMATTER);
            LocalDate periodStart = targetDate.minusDays(29);
            LocalDate periodEnd = targetDate;

            log.info("월간 랭킹 집계 시작: {} ~ {}", periodStart, periodEnd);

            List<ProductMetricsDaily> dailyList =
                    productMetricsDailyJpaRepository.findByMetricDateBetween(periodStart, periodEnd);

            List<MvProductRankMonthly> rankings = aggregateAndRankMonthly(dailyList, periodStart, periodEnd);

            mvProductRankMonthlyJpaRepository.deleteByPeriod(periodStart, periodEnd);
            mvProductRankMonthlyJpaRepository.saveAll(rankings);

            log.info("월간 랭킹 집계 완료: {} 건", rankings.size());
            return RepeatStatus.FINISHED;
        };
    }

    private List<MvProductRankWeekly> aggregateAndRankWeekly(
            List<ProductMetricsDaily> dailyList, LocalDate periodStart, LocalDate periodEnd) {

        Map<Long, double[]> aggregated = dailyList.stream()
                .collect(Collectors.groupingBy(
                        ProductMetricsDaily::getProductId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    long viewSum = list.stream().mapToLong(ProductMetricsDaily::getViewCount).sum();
                                    long likeSum = list.stream().mapToLong(ProductMetricsDaily::getLikeCount).sum();
                                    long salesSum = list.stream().mapToLong(ProductMetricsDaily::getSalesCount).sum();
                                    double score = 0.1 * viewSum + 0.2 * likeSum + 0.7 * salesSum;
                                    return new double[]{score};
                                }
                        )
                ));

        List<Map.Entry<Long, double[]>> sorted = aggregated.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<Long, double[]> e) -> e.getValue()[0]).reversed())
                .limit(TOP_RANK_LIMIT)
                .toList();

        long[] rank = {1};
        return sorted.stream()
                .map(entry -> MvProductRankWeekly.of(
                        entry.getKey(),
                        entry.getValue()[0],
                        rank[0]++,
                        periodStart,
                        periodEnd
                ))
                .toList();
    }

    private List<MvProductRankMonthly> aggregateAndRankMonthly(
            List<ProductMetricsDaily> dailyList, LocalDate periodStart, LocalDate periodEnd) {

        Map<Long, double[]> aggregated = dailyList.stream()
                .collect(Collectors.groupingBy(
                        ProductMetricsDaily::getProductId,
                        Collectors.collectingAndThen(
                                Collectors.toList(),
                                list -> {
                                    long viewSum = list.stream().mapToLong(ProductMetricsDaily::getViewCount).sum();
                                    long likeSum = list.stream().mapToLong(ProductMetricsDaily::getLikeCount).sum();
                                    long salesSum = list.stream().mapToLong(ProductMetricsDaily::getSalesCount).sum();
                                    double score = 0.1 * viewSum + 0.2 * likeSum + 0.7 * salesSum;
                                    return new double[]{score};
                                }
                        )
                ));

        List<Map.Entry<Long, double[]>> sorted = aggregated.entrySet().stream()
                .sorted(Comparator.comparingDouble((Map.Entry<Long, double[]> e) -> e.getValue()[0]).reversed())
                .limit(TOP_RANK_LIMIT)
                .toList();

        long[] rank = {1};
        return sorted.stream()
                .map(entry -> MvProductRankMonthly.of(
                        entry.getKey(),
                        entry.getValue()[0],
                        rank[0]++,
                        periodStart,
                        periodEnd
                ))
                .toList();
    }
}
