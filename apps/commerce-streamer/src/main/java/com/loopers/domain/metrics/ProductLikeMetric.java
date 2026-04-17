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
@Table(name = "product_like_metrics", indexes = {
        @Index(name = "idx_plm_bucket_time", columnList = "bucket_time")
})
@IdClass(MetricId.class)
@Getter
public class ProductLikeMetric {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "bucket_time")
    private LocalDateTime bucketTime;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    protected ProductLikeMetric() {
    }
}
