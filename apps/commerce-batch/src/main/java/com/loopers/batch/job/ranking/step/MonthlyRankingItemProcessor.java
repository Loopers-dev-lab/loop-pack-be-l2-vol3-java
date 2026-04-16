package com.loopers.batch.job.ranking.step;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.ranking.ProductRankMonthly;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class MonthlyRankingItemProcessor implements ItemProcessor<ProductMetrics, ProductRankMonthly> {

    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_ORDER = 0.7;
    private static final double WEIGHT_VIEW = 0.1;

    @Value("#{jobParameters['targetDate']}")
    private String targetDate;

    @Override
    public ProductRankMonthly process(ProductMetrics item) {
        String yearMonth = MonthlyRankingClearTasklet.toYearMonth(targetDate);
        double score = item.getLikeCount() * WEIGHT_LIKE
            + item.getOrderCount() * WEIGHT_ORDER
            + item.getViewCount() * WEIGHT_VIEW;

        return ProductRankMonthly.of(
            item.getProductId(),
            score,
            item.getLikeCount(),
            item.getOrderCount(),
            item.getViewCount(),
            yearMonth
        );
    }
}
