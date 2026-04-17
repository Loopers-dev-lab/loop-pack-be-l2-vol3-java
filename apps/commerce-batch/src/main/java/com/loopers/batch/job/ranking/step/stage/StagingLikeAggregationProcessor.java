package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Step 2 전용 — AggregatedMetric → like_count 자리에 sum 을 채운 2 건의 fan-out.
 */
@Component
@StepScope
public class StagingLikeAggregationProcessor implements ItemProcessor<AggregatedMetric, List<StagingRankingAggregation>> {

    private final String anchorDateKey;

    public StagingLikeAggregationProcessor(
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}") String anchorDateKey
    ) {
        this.anchorDateKey = anchorDateKey;
    }

    @Override
    public List<StagingRankingAggregation> process(AggregatedMetric item) {
        return List.of(
                new StagingRankingAggregation(
                        StagingAggregationProcessor.PERIOD_LAST_7D, anchorDateKey, item.productId(),
                        0L, item.sum7d(), 0L),
                new StagingRankingAggregation(
                        StagingAggregationProcessor.PERIOD_LAST_30D, anchorDateKey, item.productId(),
                        0L, item.sum30d(), 0L)
        );
    }
}
