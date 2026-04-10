package com.loopers.application.ranking;

import org.springframework.stereotype.Component;

@Component
public class RankingScorer {

    private static final double W_VIEW = 0.1;
    private static final double W_LIKE = 0.2;
    private static final double W_ORDER = 0.7;

    public double score(long viewCount, long likeCount, long quantity) {
        return W_VIEW * viewCount
                + W_LIKE * likeCount
                + W_ORDER * quantity;
    }
}
