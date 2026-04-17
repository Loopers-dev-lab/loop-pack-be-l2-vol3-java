package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import static lombok.AccessLevel.PROTECTED;

@Getter
@NoArgsConstructor(access = PROTECTED)
@Entity
@Table(name = "product_metrics_daily")
public class ProductMetricsDaily {

    @EmbeddedId
    private ProductMetricsDailyId id;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "sale_count", nullable = false)
    private int saleCount;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public ProductMetricsDaily(Long productId, LocalDate metricsDate) {
        this.id = new ProductMetricsDailyId(productId, metricsDate);
        this.likeCount = 0;
        this.viewCount = 0;
        this.saleCount = 0;
    }

    public Long getProductId() {
        return id.getProductId();
    }

    public LocalDate getMetricsDate() {
        return id.getMetricsDate();
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void incrementSaleCount(int quantity) {
        this.saleCount += quantity;
    }
}
