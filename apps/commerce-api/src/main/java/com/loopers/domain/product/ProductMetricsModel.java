package com.loopers.domain.product;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

@Entity
@Table(
        name = "product_metrics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_product_metrics_ref_product_id", columnNames = {"ref_product_id"})
        }
)
@Getter
public class ProductMetricsModel extends BaseEntity {

    @Column(name = "ref_product_id", nullable = false)
    private Long refProductId;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    protected ProductMetricsModel() {}

    private ProductMetricsModel(Long refProductId) {
        this.refProductId = refProductId;
        this.likeCount = 0L;
    }

    public static ProductMetricsModel create(Long refProductId) {
        return new ProductMetricsModel(refProductId);
    }
}
