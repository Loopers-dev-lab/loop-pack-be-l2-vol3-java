package com.loopers.batch.job.ranking.weekly.step;

import com.loopers.batch.job.ranking.common.ProductAggregate;
import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import com.loopers.batch.job.ranking.common.RankingScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 주간 집계 결과에 가중 점수를 부여한다. stateless — chunk 재시도 안전.
 */
@Component
@RequiredArgsConstructor
public class WeeklyScoreProcessor implements ItemProcessor<ProductAggregate, ProductAggregateWithScore> {

    private final RankingScoreCalculator calculator;

    @Override
    public ProductAggregateWithScore process(ProductAggregate agg) {
        BigDecimal score = calculator.calculate(
                agg.likeCount(), agg.orderCount(), agg.viewCount(), agg.orderAmount());
        return ProductAggregateWithScore.of(agg, score);
    }
}
