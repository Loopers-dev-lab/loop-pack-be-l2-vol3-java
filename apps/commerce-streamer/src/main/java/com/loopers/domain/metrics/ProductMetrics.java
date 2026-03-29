package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "product_metrics")
@Getter
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "latest_price", precision = 19, scale = 2)
    private BigDecimal latestPrice;

    @Column(name = "price_updated_at")
    private Instant priceUpdatedAt;

    protected ProductMetrics() {
    }

    public static ProductMetrics create(Long productId) {
        ProductMetrics metrics = new ProductMetrics();
        metrics.productId = productId;
        metrics.viewCount = 0;
        metrics.likeCount = 0;
        metrics.salesCount = 0;
        return metrics;
    }
}
