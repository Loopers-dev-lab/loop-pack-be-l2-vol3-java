package com.loopers.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyId.class)
public class ProductMetricsDaily {

    @Id
    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private Long likeCount = 0L;

    @Column(name = "view_count", nullable = false)
    private Long viewCount = 0L;

    @Column(name = "order_line_count", nullable = false)
    private Long orderLineCount = 0L;

    @Column(name = "order_amount", nullable = false)
    private Long orderAmount = 0L;

    protected ProductMetricsDaily() {}
}
