package com.loopers.domain.collector;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "product_metrics")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "product_id", nullable = false, unique = true)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "sales_quantity", nullable = false)
    private Long salesQuantity;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "last_event_at")
    private ZonedDateTime lastEventAt;

    public ProductMetricsModel(Long productId) {
        this.productId = productId;
        this.likeCount = 0L;
        this.salesQuantity = 0L;
        this.viewCount = 0L;
    }

    public boolean isNewerOrEqual(ZonedDateTime occurredAt) {
        return this.lastEventAt == null || !occurredAt.isBefore(this.lastEventAt);
    }

    public void applyLikeDelta(long delta, ZonedDateTime occurredAt) {
        if (!isNewerOrEqual(occurredAt)) {
            return;
        }
        this.likeCount = Math.max(0L, this.likeCount + delta);
        this.lastEventAt = occurredAt;
    }

    public void applySales(long quantity, ZonedDateTime occurredAt) {
        if (!isNewerOrEqual(occurredAt)) {
            return;
        }
        this.salesQuantity += quantity;
        this.lastEventAt = occurredAt;
    }

    public void applyView(ZonedDateTime occurredAt) {
        if (!isNewerOrEqual(occurredAt)) {
            return;
        }
        this.viewCount += 1;
        this.lastEventAt = occurredAt;
    }
}
