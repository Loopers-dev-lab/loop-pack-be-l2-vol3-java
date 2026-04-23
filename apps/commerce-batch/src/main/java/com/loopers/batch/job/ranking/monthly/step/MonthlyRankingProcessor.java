package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.RankingScoreCalculator;
import com.loopers.batch.job.ranking.ScoredProductMetrics;
import com.loopers.batch.job.ranking.monthly.MonthlyRankingJobConfig;
import com.loopers.domain.ranking.BatchProductMetricsModel;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = MonthlyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class MonthlyRankingProcessor implements ItemProcessor<BatchProductMetricsModel, ScoredProductMetrics> {

    private final RankingScoreCalculator rankingScoreCalculator;

    @Override
    public ScoredProductMetrics process(BatchProductMetricsModel item) {
        double score = rankingScoreCalculator.calculate(
            item.getViewCount(),
            item.getLikeCount(),
            item.getSalesQuantity()
        );
        return new ScoredProductMetrics(
            item.getProductId(),
            score,
            item.getViewCount(),
            item.getLikeCount(),
            item.getSalesQuantity()
        );
    }
}
