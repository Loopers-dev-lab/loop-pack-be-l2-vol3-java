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

    /**
     * occurredAt 기준으로 최신 이벤트만 반영한다.
     * 오래된 이벤트가 늦게 도착해 최신 상태를 덮어쓰는 것을 방지한다.
     */
    public boolean increaseLikes(long occurredAtMillis) {
        if (!isNewer(occurredAtMillis)) return false;
        this.likesCount++;
        this.updatedAt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(occurredAtMillis), java.time.ZoneId.systemDefault());
        return true;
    }

    public boolean decreaseLikes(long occurredAtMillis) {
        if (!isNewer(occurredAtMillis)) return false;
        if (this.likesCount > 0) this.likesCount--;
        this.updatedAt = ZonedDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(occurredAtMillis), java.time.ZoneId.systemDefault());
        return true;
    }

    private boolean isNewer(long occurredAtMillis) {
        return occurredAtMillis > this.updatedAt.toInstant().toEpochMilli();
    }

    public Long getProductId() { return productId; }
    public long getLikesCount() { return likesCount; }
    public ZonedDateTime getUpdatedAt() { return updatedAt; }
}
