package com.loopers.ranking;

import java.math.BigDecimal;

public class ScoreCalculator {

    private final RankingWeightProperties weights;

    public ScoreCalculator(RankingWeightProperties weights) {
        this.weights = weights;
    }

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

    public double calculateTotal(long viewCount, long likeCount, BigDecimal orderAmount) {
        return calculateTotal(viewCount, likeCount, orderAmount.doubleValue());
    }
}
