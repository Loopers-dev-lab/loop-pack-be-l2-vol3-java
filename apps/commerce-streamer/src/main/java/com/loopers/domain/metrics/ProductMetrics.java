package com.loopers.domain.metrics;

import java.time.LocalDate;

public class ProductMetrics {

    private final Long id;
    private final Long productId;
    private final LocalDate metricsDate;
    private final int likeCount;
    private final int viewCount;
    private final int salesCount;
    private final int totalQuantity;

    private ProductMetrics(Long id, Long productId, LocalDate metricsDate, int likeCount, int viewCount, int salesCount, int totalQuantity) {
        this.id = id;
        this.productId = productId;
        this.metricsDate = metricsDate;
        this.likeCount = likeCount;
        this.viewCount = viewCount;
        this.salesCount = salesCount;
        this.totalQuantity = totalQuantity;
    }

    public static ProductMetrics create(Long productId, LocalDate metricsDate) {
        return new ProductMetrics(null, productId, metricsDate, 0, 0, 0, 0);
    }

    public static ProductMetrics restore(Long id, Long productId, LocalDate metricsDate, int likeCount, int viewCount, int salesCount, int totalQuantity) {
        return new ProductMetrics(id, productId, metricsDate, likeCount, viewCount, salesCount, totalQuantity);
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public LocalDate getMetricsDate() {
        return metricsDate;
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

    public int getTotalQuantity() {
        return totalQuantity;
    }
}
