package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * 상품별 일간 이벤트 메트릭 집계 테이블.
 *
 * Kafka Consumer가 이벤트를 수신할 때마다 해당 날짜의 카운트를 증감한다.
 * 복합 PK (product_id + metric_date) 로 상품·날짜 단위 유일성을 보장한다.
 */
@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyId.class)
public class ProductMetricsDaily {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "metric_date")
    private LocalDate metricDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    protected ProductMetricsDaily() {}

    public static ProductMetricsDaily init(Long productId, LocalDate metricDate) {
        ProductMetricsDaily m = new ProductMetricsDaily();
        m.productId = productId;
        m.metricDate = metricDate;
        m.viewCount = 0;
        m.likeCount = 0;
        m.orderCount = 0;
        return m;
    }

    /**
     * 이벤트 타입에 따라 해당 카운터를 증감한다.
     *
     * @param eventType VIEWED, LIKED, UNLIKED, ORDERED 중 하나
     */
    public void record(String eventType) {
        switch (eventType) {
            case "VIEWED" -> this.viewCount++;
            case "LIKED" -> this.likeCount++;
            case "UNLIKED" -> { if (this.likeCount > 0) this.likeCount--; }
            case "ORDERED" -> this.orderCount++;
            default -> throw new IllegalArgumentException("알 수 없는 이벤트 타입: " + eventType);
        }
    }

    public Long getProductId() { return productId; }
    public LocalDate getMetricDate() { return metricDate; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getOrderCount() { return orderCount; }
}
