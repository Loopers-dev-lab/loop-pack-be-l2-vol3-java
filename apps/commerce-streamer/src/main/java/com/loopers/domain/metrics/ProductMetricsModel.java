package com.loopers.domain.metrics;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Check;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_metrics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_metrics_ref_product_id", columnNames = {"ref_product_id"})
        }
)
@Check(name = "chk_like_count_non_negative", constraints = "like_count >= 0")
@Getter
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "ref_product_id", nullable = false)
    private Long refProductId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;

    protected ProductMetricsModel() {}

    private ProductMetricsModel(Long refProductId, long likeCount, LocalDateTime lastEventAt) {
        this.refProductId = refProductId;
        this.likeCount = likeCount;
        this.lastEventAt = lastEventAt;
    }

    public static ProductMetricsModel create(Long refProductId, int delta, LocalDateTime eventAt) {
        return new ProductMetricsModel(refProductId, Math.max(0L, delta), eventAt);
    }

    public void applyDelta(int delta, LocalDateTime eventAt) {
        if (this.lastEventAt != null && !eventAt.isAfter(this.lastEventAt)) {
            return;
        }
        this.likeCount = Math.max(0, this.likeCount + delta);
        this.lastEventAt = eventAt;
    }
}
