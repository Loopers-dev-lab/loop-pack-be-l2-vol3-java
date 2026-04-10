package com.loopers.domain.metrics;

import jakarta.persistence.*;
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
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "likes_count", nullable = false)
    private Long likesCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "order_count", nullable = false)
    private Long orderCount;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    private ProductMetrics(Long productId) {
        this.productId = productId;
        this.likesCount = 0L;
        this.viewCount = 0L;
        this.orderCount = 0L;
    }

    public static ProductMetrics create(Long productId) {
        return new ProductMetrics(productId);
    }

    public void increaseLikes() {
        this.likesCount++;
    }

    public void decreaseLikes() {
        if (this.likesCount > 0) {
            this.likesCount--;
        }
    }

    public void increaseViews() {
        this.viewCount++;
    }

    public void increaseOrders(int count) {
        this.orderCount += count;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
