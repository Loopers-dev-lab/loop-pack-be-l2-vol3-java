package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 일별 상품 메트릭 스냅샷 엔티티.
 *
 * <p>Session 10 배치 랭킹 집계의 원천 테이블.
 * Consumer가 이벤트 처리 시 누적 테이블({@link ProductMetricsModel})과 동시에
 * 같은 트랜잭션에서 upsert한다.</p>
 *
 * <p>PK = (metric_date, product_id). 날짜 범위 스캔 최적화.
 * metric_date는 이벤트 occurredAt을 KST(Asia/Seoul)로 변환한 LocalDate.</p>
 */
@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetricsDailyModel {

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
