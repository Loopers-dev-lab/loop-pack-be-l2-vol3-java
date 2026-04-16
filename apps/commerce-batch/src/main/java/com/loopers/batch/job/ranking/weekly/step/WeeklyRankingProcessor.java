package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.RankingScoreCalculator;
import com.loopers.batch.job.ranking.ScoredProductMetrics;
import com.loopers.batch.job.ranking.weekly.WeeklyRankingJobConfig;
import com.loopers.domain.ranking.BatchProductMetricsModel;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@ConditionalOnProperty(name = "spring.batch.job.name", havingValue = WeeklyRankingJobConfig.JOB_NAME)
@RequiredArgsConstructor
@Component
public class WeeklyRankingProcessor implements ItemProcessor<BatchProductMetricsModel, ScoredProductMetrics> {

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
