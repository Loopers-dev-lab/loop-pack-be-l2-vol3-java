package com.loopers.domain.ranking;

import lombok.Getter;

import java.util.Objects;

@Getter
public class ProductMetricsAggregation {

    private final Long productId;
    private final Long likeCount;
    private final Long viewCount;
    private final Long salesCount;
    private final Long salesAmount;
    private final Double score;
    private final Integer rank;

    public ProductMetricsAggregation(Long productId, Long likeCount, Long viewCount,
                                     Long salesCount, Long salesAmount, Integer rank) {
        this.productId = productId;
        this.likeCount = Objects.requireNonNullElse(likeCount, 0L);
        this.viewCount = Objects.requireNonNullElse(viewCount, 0L);
        this.salesCount = Objects.requireNonNullElse(salesCount, 0L);
        this.salesAmount = Objects.requireNonNullElse(salesAmount, 0L);
        this.score = this.viewCount * 0.1 + this.likeCount * 0.2 + this.salesAmount * 0.6;
        this.rank = rank;
    }
}
