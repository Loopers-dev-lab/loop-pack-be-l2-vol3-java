package com.loopers.domain.ranking;

/**
 * {@code product_metrics}의 조회·좋아요·판매(건수) 집계와 대응한다.
 */
public record RankingMetricCounts(long viewCount, long likeCount, long soldQuantity) {

    public RankingMetricCounts {
        if (viewCount < 0L || likeCount < 0L || soldQuantity < 0L) {
            throw new IllegalArgumentException("metric counts must be non-negative");
        }
    }
}
