package com.loopers.batch.job.ranking.monthly.step;

import com.loopers.batch.job.ranking.common.ProductAggregate;
import com.loopers.batch.job.ranking.common.ProductAggregateWithScore;
import com.loopers.batch.job.ranking.common.RankingScoreCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class MonthlyScoreProcessor implements ItemProcessor<ProductAggregate, ProductAggregateWithScore> {

    private final RankingScoreCalculator calculator;

    @Override
    public ProductAggregateWithScore process(ProductAggregate agg) {
        BigDecimal score = calculator.calculate(
                agg.likeCount(), agg.orderCount(), agg.viewCount(), agg.orderAmount());
        return ProductAggregateWithScore.of(agg, score);
    }
}
