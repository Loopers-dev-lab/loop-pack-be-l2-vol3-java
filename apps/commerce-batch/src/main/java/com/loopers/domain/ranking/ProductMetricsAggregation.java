package com.loopers.domain.ranking;

import lombok.Getter;

@Getter
public class ProductMetricsAggregation {

    private static final double WEIGHT_VIEW = 0.1;
    private static final double WEIGHT_LIKE = 0.2;
    private static final double WEIGHT_SALES = 0.7;

    // Saturation 상수 (x/(x+k) 수식의 k).
    // TODO: 실제 운영 데이터 분포(중앙값, 상위 TOP N의 평균)를 바탕으로 지표별로 튜닝 필요.
    //       현재는 첫 도입 단계라 임의값 100으로 통일.
    private static final double SATURATION_K = 100.0;

    private final Long productId;
    private final long viewCount;
    private final long likeCount;
    private final long salesCount;

    public ProductMetricsAggregation(Long productId, long viewCount, long likeCount, long salesCount) {
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
    }

    public double calculateScore() {
        return saturate(viewCount) * WEIGHT_VIEW
            + saturate(likeCount) * WEIGHT_LIKE
            + saturate(salesCount) * WEIGHT_SALES;
    }

    /**
     * Saturation 함수 x/(x+k).
     * 큰 값일수록 1에 수렴하여 이상치가 점수를 지배하지 못하도록 한다.
     * 음수는 0으로 간주한다.
     */
    private double saturate(long count) {
        if (count <= 0) return 0.0;
        return (double) count / (count + SATURATION_K);
    }
}
