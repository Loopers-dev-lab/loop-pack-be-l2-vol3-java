package com.loopers.infrastructure.persistence.jpa.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 상품 메트릭 JPA 엔티티.
 */
@Entity
@Table(
    name = "product_metrics",
    indexes = {
        @Index(name = "idx_product_metrics_product_id", columnList = "product_id", unique = true)
    }
)
public class ProductMetricsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "order_total_amount", nullable = false)
    private long orderTotalAmount;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_event_timestamp")
    private Instant lastEventTimestamp;

    protected ProductMetricsJpaEntity() {}

    public ProductMetricsJpaEntity(Long productId) {
        this.productId = productId;
        this.likeCount = 0;
        this.viewCount = 0;
        this.orderCount = 0;
        this.orderTotalAmount = 0;
    }

    @PrePersist
    @PreUpdate
    private void prePersistOrUpdate() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public long getLikeCount() {
        return likeCount;
    }

    public void setLikeCount(long likeCount) {
        this.likeCount = likeCount;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public long getOrderCount() {
        return orderCount;
    }

    public void setOrderCount(long orderCount) {
        this.orderCount = orderCount;
    }

    public long getOrderTotalAmount() {
        return orderTotalAmount;
    }

    public void setOrderTotalAmount(long orderTotalAmount) {
        this.orderTotalAmount = orderTotalAmount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getLastEventTimestamp() {
        return lastEventTimestamp;
    }

    public void setLastEventTimestamp(Instant lastEventTimestamp) {
        this.lastEventTimestamp = lastEventTimestamp;
    }
}
