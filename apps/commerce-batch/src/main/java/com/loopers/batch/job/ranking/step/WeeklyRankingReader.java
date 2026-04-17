package com.loopers.batch.job.ranking.step;

import com.loopers.batch.job.ranking.WeeklyRankingJobConfig;
import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.ranking.ProductMetricsAggregation;
import com.loopers.domain.ranking.RankingScoreCalculator;
import com.loopers.infrastructure.metrics.ProductMetricsDailyBatchRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@StepScope
@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@Component
public class WeeklyRankingReader implements ItemReader<ProductMetricsAggregation> {

    private final ProductMetricsDailyBatchRepository metricsRepository;
    private final RankingScoreCalculator scoreCalculator;
    private Iterator<ProductMetricsAggregation> iterator;

    @Value("#{jobParameters['requestDate']}")
    private LocalDate requestDate;

    public WeeklyRankingReader(ProductMetricsDailyBatchRepository metricsRepository,
                               RankingScoreCalculator scoreCalculator) {
        this.metricsRepository = metricsRepository;
        this.scoreCalculator = scoreCalculator;
    }

    @Override
    public ProductMetricsAggregation read() {
        if (iterator == null) {
            initialize();
        }
        return iterator.hasNext() ? iterator.next() : null;
    }

    private void initialize() {
        LocalDate endDate = requestDate;
        LocalDate startDate = endDate.minusDays(6);
        log.info("주간 메트릭 읽기: {} ~ {}", startDate, endDate);

        List<ProductMetricsDaily> dailyMetrics = metricsRepository.findByDateRange(startDate, endDate);

        Map<Long, int[]> aggregated = new LinkedHashMap<>();
        for (ProductMetricsDaily m : dailyMetrics) {
            aggregated.computeIfAbsent(m.getProductId(), k -> new int[3]);
            int[] counts = aggregated.get(m.getProductId());
            counts[0] += m.getViewCount();
            counts[1] += m.getLikeCount();
            counts[2] += m.getSaleCount();
        }

        List<ProductMetricsAggregation> result = new ArrayList<>();
        for (var entry : aggregated.entrySet()) {
            int[] counts = entry.getValue();
            double score = scoreCalculator.calculateScore(counts[0], counts[1], counts[2]);
            result.add(new ProductMetricsAggregation(entry.getKey(), counts[0], counts[1], counts[2], score));
        }

        result.sort(Comparator.comparingDouble(ProductMetricsAggregation::score).reversed());
        iterator = result.iterator();
        log.info("주간 랭킹 집계 완료: {}개 상품", result.size());
    }
}
