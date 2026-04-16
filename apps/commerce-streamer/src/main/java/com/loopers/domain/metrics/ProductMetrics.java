package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_metrics")
@Getter
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "order_count", nullable = false)
    private Long orderCount = 0L;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public ProductMetrics(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.orderCount = 0L;
        this.viewCount = 0L;
        this.updatedAt = ZonedDateTime.now();
    }

    public void increaseLikeCount(ZonedDateTime eventTime) {
        if (eventTime.isAfter(this.updatedAt)) {
            this.likeCount++;
            this.updatedAt = eventTime;
        }
    }

    public void decreaseLikeCount(ZonedDateTime eventTime) {
        if (eventTime.isAfter(this.updatedAt)) {
            if (this.likeCount > 0) {
                this.likeCount--;
            }
            this.updatedAt = eventTime;
        }
    }

    public void increaseViewCount(ZonedDateTime eventTime) {
        if (eventTime.isAfter(this.updatedAt)) {
            this.viewCount++;
            this.updatedAt = eventTime;
        }
    }

    public void increaseOrderCount(ZonedDateTime eventTime) {
        if (eventTime.isAfter(this.updatedAt)) {
            this.orderCount++;
            this.updatedAt = eventTime;
        }
    }
}
