package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

/**
 * 상품 집계 테이블 (비정규화)
 *
 * catalog-events Consumer가 이벤트를 수신할 때마다 upsert한다.
 * version 기반으로 최신 이벤트만 반영 (오래된 이벤트가 늦게 도착해도 덮어쓰지 않음).
 */
@Entity
@Table(name = "product_metrics")
public class ProductMetrics {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "likes_count", nullable = false)
    private long likesCount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected ProductMetrics() {}

    public static ProductMetrics init(Long productId) {
        ProductMetrics m = new ProductMetrics();
        m.productId = productId;
        m.likesCount = 0;
        m.updatedAt = ZonedDateTime.now();
        return m;
    }

    public void increaseLikes() {
        this.likesCount++;
        this.updatedAt = ZonedDateTime.now();
    }

    public void decreaseLikes() {
        if (this.likesCount > 0) this.likesCount--;
        this.updatedAt = ZonedDateTime.now();
    }

    public Long getProductId() { return productId; }
    public long getLikesCount() { return likesCount; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
