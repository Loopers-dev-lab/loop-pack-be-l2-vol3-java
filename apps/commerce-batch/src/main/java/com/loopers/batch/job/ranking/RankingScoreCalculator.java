package com.loopers.batch.job.ranking;

/**
 * 배치 랭킹 점수 계산 공식.
 * Streamer 가중치 (LikeEventConsumer: 0.2, OrderEventConsumer: 0.7) 와 일치시킨다.
 */
public final class RankingScoreCalculator {

    public static final double LIKE_WEIGHT = 0.2;
    public static final double ORDER_WEIGHT = 0.7;

    private RankingScoreCalculator() {
    }

    public static double calculate(int likeCount, int orderCount) {
        return likeCount * LIKE_WEIGHT + orderCount * ORDER_WEIGHT;
    }
}
