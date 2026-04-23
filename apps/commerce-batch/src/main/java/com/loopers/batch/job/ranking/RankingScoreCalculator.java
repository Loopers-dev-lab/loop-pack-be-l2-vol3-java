package com.loopers.batch.job.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingScoreCalculator {

    private static final double VIEW_WEIGHT = 0.1;
    private static final double LIKE_WEIGHT = 0.2;
    private static final double SALES_WEIGHT = 0.6;

    public double calculate(long viewCount, long likeCount, long salesQuantity) {
        return viewCount * VIEW_WEIGHT + likeCount * LIKE_WEIGHT + salesQuantity * SALES_WEIGHT;
    }
}
