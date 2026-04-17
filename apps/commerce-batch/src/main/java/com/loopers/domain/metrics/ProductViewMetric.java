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
 *
 * 이 엔티티는 배치가 테이블을 "읽기 위한 schema 정의" 로만 존재한다:
 * - 프로덕션에서는 streamer 가 이 테이블을 소유하고 UPSERT 한다
 * - 배치는 JdbcCursorItemReader 로 원시 row 만 스트리밍하여 App 에서 집계한다
 * - 테스트 환경에서 ddl-auto 가 동일 스키마를 생성할 수 있도록 미러 엔티티를 둔다
 */
@Entity
@Table(name = "product_view_metrics", indexes = {
        @Index(name = "idx_pvm_bucket_time", columnList = "bucket_time")
})
@IdClass(ProductMetricId.class)
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
