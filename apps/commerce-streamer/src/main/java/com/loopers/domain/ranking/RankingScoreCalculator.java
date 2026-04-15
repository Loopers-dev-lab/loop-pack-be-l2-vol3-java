package com.loopers.domain.ranking;

/**
 * {@link RankingMetricCounts}와 {@link RankingScoreWeights}로 총점을 계산한다.
 */
public class RankingScoreCalculator {

    private final RankingScoreWeights weights;

    public RankingScoreCalculator(RankingScoreWeights weights) {
        this.weights = weights;
    }

    /**
     * @param counts 비음수 집계 값
     * @return 합산 점수 (조회 * 가중치 + 좋아요 * 가중치 + 판매 * 가중치)
     */
    public double calculate(RankingMetricCounts counts) {
        return weights.viewWeight() * counts.viewCount()
                + weights.likeWeight() * counts.likeCount()
                + weights.orderWeight() * counts.soldQuantity();
    }
}
