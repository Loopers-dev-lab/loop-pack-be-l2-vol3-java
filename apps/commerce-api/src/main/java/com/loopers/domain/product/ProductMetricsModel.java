package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import org.hibernate.annotations.Check;

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

    protected ProductMetricsModel() {}

    private ProductMetricsModel(Long refProductId, long initialLikeCount) {
        this.refProductId = refProductId;
        this.likeCount = initialLikeCount;
    }

    public static ProductMetricsModel create(Long refProductId, int initialDelta) {
        return new ProductMetricsModel(refProductId, Math.max(0L, initialDelta));
    }

    public void adjustLikeCount(int delta) {
        this.likeCount += delta;
    }
}
