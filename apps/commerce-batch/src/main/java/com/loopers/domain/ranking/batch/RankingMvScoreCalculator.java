package com.loopers.domain.ranking.batch;

/**
 * 스트리머 {@code RankingScoreCalculator}와 동일한 기본 가중치(0.1 / 0.2 / 0.6)로 점수를 계산한다.
 * commerce-streamer 모듈에 의존하지 않기 위해 배치 도메인에 둔다.
 */
public final class RankingMvScoreCalculator {

    private static final double VIEW_WEIGHT = 0.1d;
    private static final double LIKE_WEIGHT = 0.2d;
    private static final double ORDER_WEIGHT = 0.6d;

    private RankingMvScoreCalculator() {
    }

    /**
     * @param viewCount   조회 수(비음수)
     * @param likeCount   좋아요 수(비음수)
     * @param soldQuantity 판매 수량(비음수)
     * @return 가중 합산 점수
     */
    public static double score(long viewCount, long likeCount, long soldQuantity) {
        if (viewCount < 0L || likeCount < 0L || soldQuantity < 0L) {
            throw new IllegalArgumentException("metric counts must be non-negative");
        }
        return VIEW_WEIGHT * viewCount + LIKE_WEIGHT * likeCount + ORDER_WEIGHT * soldQuantity;
    }
}
