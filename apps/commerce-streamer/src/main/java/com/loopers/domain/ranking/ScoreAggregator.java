package com.loopers.domain.ranking;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class ScoreAggregator {

    private final RankingWeightProperties weights;

    public double scoreForView() {
        return weights.view();
    }

    public double scoreForLike(int delta) {
        return weights.like() * delta;
    }

    public double scoreForOrder(BigDecimal price, int quantity) {
        return weights.order() * price.doubleValue() * quantity;
    }

    public double calculateTotal(long viewCount, long likeCount, double orderAmount) {
        return weights.view() * viewCount
                + weights.like() * likeCount
                + weights.order() * orderAmount;
    }
}
