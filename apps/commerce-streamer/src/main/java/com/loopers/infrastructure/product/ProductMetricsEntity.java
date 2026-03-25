package com.loopers.infrastructure.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * 상품 메트릭 집계 테이블
 * PK = product_id → 상품당 1 row → upsert로 집계
 */
@Entity
@Table(name = "product_metrics")
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

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetricsEntity() {}

    public static ProductMetricsEntity create(Long productId) {
        ProductMetricsEntity entity = new ProductMetricsEntity();
        entity.productId = productId;
        entity.viewCount = 0;
        entity.likeCount = 0;
        entity.salesCount = 0;
        entity.updatedAt = ZonedDateTime.now();
        return entity;
    }

    public void incrementViewCount() {
        this.viewCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void incrementLikeCount() {
        this.likeCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void decrementLikeCount() {
        if (this.likeCount > 0) this.likeCount--;
        this.updatedAt = ZonedDateTime.now();
    }

    public void addSalesCount(int quantity) {
        this.salesCount += quantity;
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getProductId() { return productId; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getSalesCount() { return salesCount; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
