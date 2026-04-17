package com.loopers.domain.metrics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * commerce-streamer 가 관리하는 원천 시계열 테이블의 배치 측 읽기 모델.
 * ProductViewMetric 의 주석 참고.
 */
@Entity
@Table(name = "product_like_metrics", indexes = {
        @Index(name = "idx_plm_bucket_time", columnList = "bucket_time")
})
@IdClass(ProductMetricId.class)
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

    public ProductLikeMetric(Long productId, LocalDateTime bucketTime, long likeCount) {
        this.productId = productId;
        this.bucketTime = bucketTime;
        this.likeCount = likeCount;
    }
}
