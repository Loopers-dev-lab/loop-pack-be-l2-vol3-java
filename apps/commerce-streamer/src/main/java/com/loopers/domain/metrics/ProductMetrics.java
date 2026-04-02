package com.loopers.domain.metrics;

public class ProductMetrics {

    private final Long id;
    private final Long productId;
    private final int likeCount;
    private final int viewCount;
    private final int salesCount;

    private ProductMetrics(Long id, Long productId, int likeCount, int viewCount, int salesCount) {
        this.id = id;
        this.productId = productId;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.salesCount = salesCount;
    }

    public static ProductMetrics create(Long productId) {
        return new ProductMetrics(null, productId, 0, 0, 0);
    }

    public static ProductMetrics restore(Long id, Long productId, int likeCount, int viewCount, int salesCount) {
        return new ProductMetrics(id, productId, likeCount, viewCount, salesCount);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getLikeCount() {
        return likeCount;
    }

    public int getViewCount() {
        return viewCount;
    }

    public int getSalesCount() {
        return salesCount;
    }
}
