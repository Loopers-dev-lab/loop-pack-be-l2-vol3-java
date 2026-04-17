package com.loopers.infrastructure.metrics;

public class ProductMetricsAggregatedDto {

    private final Long productId;
    private final long totalViewCount;
    private final long totalLikeCount;
    private final long totalQuantity;

    public ProductMetricsAggregatedDto(Long productId, long totalViewCount, long totalLikeCount, long totalQuantity) {
        this.productId = productId;
        this.totalViewCount = totalViewCount;
        this.totalLikeCount = totalLikeCount;
        this.totalQuantity = totalQuantity;
    }

    public Long getProductId() {
        return productId;
    }

    public long getTotalViewCount() {
        return totalViewCount;
    }

    public long getTotalLikeCount() {
        return totalLikeCount;
    }

    public long getTotalQuantity() {
        return totalQuantity;
    }
}
