package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "order_count", nullable = false)
    private Long orderCount = 0L;

    private ProductMetrics(Long productId) {
        this.productId = productId;
    }

    public static ProductMetrics of(Long productId) {
        return new ProductMetrics(productId);
    }

    public Long likeCount() {
        return likeCount;
    }

    public Long orderCount() {
        return orderCount;
    }

    public void applyLike(int delta) {
        this.likeCount += delta;
    }

    public void applyOrder(long quantity) {
        this.orderCount += quantity;
    }
}
