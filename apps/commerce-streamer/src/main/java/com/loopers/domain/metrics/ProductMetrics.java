package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    private ProductMetrics(Long productId) {
        this.productId = productId;
        this.likeCount = 0;
        this.viewCount = 0;
        this.salesCount = 0;
        this.updatedAt = ZonedDateTime.now();
    }

    public static ProductMetrics create(Long productId) {
        return new ProductMetrics(productId);
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
        this.updatedAt = ZonedDateTime.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void incrementSalesCount() {
        this.salesCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void decrementSalesCount() {
        if (this.salesCount > 0) {
            this.salesCount--;
        }
        this.updatedAt = ZonedDateTime.now();
    }
}
