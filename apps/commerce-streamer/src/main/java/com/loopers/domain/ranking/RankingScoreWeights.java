package com.loopers.domain.ranking;

/**
 * 랭킹 총점 선형 결합의 가중치.
 */
public record RankingScoreWeights(double viewWeight, double likeWeight, double orderWeight) {

    /**
     * 가중치를 초기화한다.
     * @param viewWeight 조회 가중치
     * @param likeWeight 좋아요 가중치
     * @param orderWeight 판매 가중치
     */
    public RankingScoreWeights {
        if (viewWeight < 0.0d || likeWeight < 0.0d || orderWeight < 0.0d) {
            throw new IllegalArgumentException("weights must be non-negative");
        }
    }

    /**
     * 가중치 (조회: 0.1, 좋아요: 0.2, 판매: 0.6)
     */
    public static RankingScoreWeights questExample() {
        return new RankingScoreWeights(0.1d, 0.2d, 0.6d);
    }
}
