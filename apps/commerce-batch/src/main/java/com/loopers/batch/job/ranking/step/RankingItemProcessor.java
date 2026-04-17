package com.loopers.batch.job.ranking.step;

import com.loopers.infrastructure.metrics.ProductMetricsAggregatedDto;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

@StepScope
@Component
public class RankingItemProcessor implements ItemProcessor<ProductMetricsAggregatedDto, RankedProductDto> {

    @Override
    public RankedProductDto process(ProductMetricsAggregatedDto dto) {
        double score = 0.1 * dto.getTotalViewCount()
                + 0.2 * dto.getTotalLikeCount()
                + 0.7 * Math.log1p(dto.getTotalQuantity());
        return new RankedProductDto(dto.getProductId(), score);
    }
}
