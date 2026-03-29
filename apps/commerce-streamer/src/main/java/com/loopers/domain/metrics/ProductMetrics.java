package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_metrics")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long productId;

    @Column(nullable = false)
    private Long likeCount;

    @Column(nullable = false)
    private Long orderCount;

    @Column(nullable = false)
    private Long viewCount;

    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    public static ProductMetrics create(Long productId) {
        ProductMetrics metrics = new ProductMetrics();
        metrics.productId = productId;
        metrics.likeCount = 0L;
        metrics.orderCount = 0L;
        metrics.viewCount = 0L;
        return metrics;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
    }

    public void addOrderCount(Long quantity) {
        this.orderCount += quantity;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    @PrePersist
    private void prePersist() {
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
