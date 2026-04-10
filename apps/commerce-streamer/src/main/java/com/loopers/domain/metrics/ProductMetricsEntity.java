package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsEntity {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
        this.version++;
    }

    public static ProductMetricsEntity createWithView(Long productId) {
        ProductMetricsEntity entity = new ProductMetricsEntity();
        entity.productId = productId;
        entity.viewCount = 1;
        return entity;
    }

    public static ProductMetricsEntity createWithLike(Long productId, long delta) {
        ProductMetricsEntity entity = new ProductMetricsEntity();
        entity.productId = productId;
        entity.likeCount = delta;
        return entity;
    }

    public static ProductMetricsEntity createWithSales(Long productId, long count) {
        ProductMetricsEntity entity = new ProductMetricsEntity();
        entity.productId = productId;
        entity.salesCount = count;
        return entity;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public void addLikeCount(long delta) {
        this.likeCount += delta;
    }

    public void addSalesCount(long count) {
        this.salesCount += count;
    }
}
