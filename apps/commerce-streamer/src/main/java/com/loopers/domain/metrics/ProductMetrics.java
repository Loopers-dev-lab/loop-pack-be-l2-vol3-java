package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount = 0L;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public ProductMetrics(Long productId) {
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getLikeCount() {
        return likeCount;
    }

    public Long getSalesCount() {
        return salesCount;
    }

    public Long getViewCount() {
        return viewCount;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}