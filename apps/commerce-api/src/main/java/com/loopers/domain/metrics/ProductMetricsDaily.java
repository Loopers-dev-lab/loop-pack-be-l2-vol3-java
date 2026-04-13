package com.loopers.domain.metrics;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_metrics_daily", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"product_id", "metric_date"})
})
public class ProductMetricsDaily {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ProductMetricsDaily() {}

    public static ProductMetricsDaily of(Long productId, LocalDate metricDate,
                                          long viewCount, long likeCount, long salesCount) {
        ProductMetricsDaily entity = new ProductMetricsDaily();
        entity.productId = productId;
        entity.metricDate = metricDate;
        entity.viewCount = viewCount;
        entity.likeCount = likeCount;
        entity.salesCount = salesCount;
        entity.createdAt = LocalDateTime.now();
        return entity;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
