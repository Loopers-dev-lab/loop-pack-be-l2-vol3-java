package com.loopers.domain.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingScoreCalculator {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double ORDER_WEIGHT = 0.7;

    public double viewScore() {
        return VIEW_WEIGHT;
    }

    public double likeScore() {
        return LIKE_WEIGHT;
    }

    public double orderScore(long price, int amount) {
        long totalAmount = price * amount;
        if (totalAmount <= 0) return 0.0;
        return ORDER_WEIGHT * Math.log10(totalAmount);
    }
}
