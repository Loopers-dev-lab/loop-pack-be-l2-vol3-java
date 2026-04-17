package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "product_view_metrics", indexes = {
        @Index(name = "idx_pvm_bucket_time", columnList = "bucket_time")
})
@IdClass(MetricId.class)
@Getter
public class ProductViewMetric {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "bucket_time")
    private LocalDateTime bucketTime;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    protected ProductViewMetric() {
    }

    public ProductViewMetric(Long productId, LocalDateTime bucketTime, long viewCount) {
        this.productId = productId;
        this.bucketTime = bucketTime;
        this.viewCount = viewCount;
    }
}
