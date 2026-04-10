package com.loopers.infrastructure.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_metrics")
public class ProductMetricsEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long version;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public static ProductMetricsEntity createNew(Long productId) {
        ProductMetricsEntity entity = new ProductMetricsEntity();
        entity.productId = productId;
        entity.likeCount = 0;
        entity.orderCount = 0;
        entity.viewCount = 0;
        entity.version = 0;
        entity.updatedAt = LocalDateTime.now();
        return entity;
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) {
            this.likeCount--;
        }
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void incrementOrderCount() {
        this.orderCount++;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateVersion(long version) {
        this.version = version;
        this.updatedAt = LocalDateTime.now();
    }
}
