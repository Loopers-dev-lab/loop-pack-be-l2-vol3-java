package com.loopers.batch.job.ranking.step.stage;

import com.loopers.batch.job.ranking.param.RankingJobParametersListener;
import com.loopers.domain.ranking.staging.StagingRankingAggregation;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AggregatedMetric(productId, sum7d, sum30d) → {@code List<StagingRankingAggregation>} 2건.
 *
 * <p>순수 변환 (I/O 없음, 상태 없음). Chunk 철학의 fan-out 패턴:
 * 각 output 이 서로 독립적이어야 하는데 LAST_7D / LAST_30D row 는 독립이므로 OK.</p>
 */
@Component
@StepScope
public class StagingAggregationProcessor implements ItemProcessor<AggregatedMetric, List<StagingRankingAggregation>> {

    public static final String PERIOD_LAST_7D = "LAST_7D";
    public static final String PERIOD_LAST_30D = "LAST_30D";

    private final String anchorDateKey;

    public StagingAggregationProcessor(
            @Value("#{jobExecutionContext['" + RankingJobParametersListener.CTX_ANCHOR_DATE_KEY + "']}") String anchorDateKey
    ) {
        this.anchorDateKey = anchorDateKey;
    }

    @Override
    public List<StagingRankingAggregation> process(AggregatedMetric item) {
        return List.of(
                new StagingRankingAggregation(
                        PERIOD_LAST_7D, anchorDateKey, item.productId(),
                        item.sum7d(), 0L, 0L),
                new StagingRankingAggregation(
                        PERIOD_LAST_30D, anchorDateKey, item.productId(),
                        item.sum30d(), 0L, 0L)
        );
    }
}
