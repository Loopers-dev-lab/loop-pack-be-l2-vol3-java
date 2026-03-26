package com.loopers.infrastructure.collector;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

import static lombok.AccessLevel.PROTECTED;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = PROTECTED)
public class ProductMetricsModel {

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "last_event_occurred_at")
    private Instant lastEventOccurredAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    private ProductMetricsModel(Long productId, long likeCount, Instant lastEventOccurredAt) {
        this.productId = productId;
        this.likeCount = likeCount;
        this.lastEventOccurredAt = lastEventOccurredAt;
        this.updatedAt = Instant.now();
    }

    public static ProductMetricsModel initialize(Long productId, Instant occurredAt) {
        return new ProductMetricsModel(productId, 0L, occurredAt);
    }

    public void applyLikeDelta(long delta, Instant occurredAt) {
        this.likeCount += delta;
        this.lastEventOccurredAt = occurredAt;
        this.updatedAt = Instant.now();
    }
}
