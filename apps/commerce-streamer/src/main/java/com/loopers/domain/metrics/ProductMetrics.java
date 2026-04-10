package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "product_metrics", indexes = {
    @Index(name = "idx_metric_date", columnList = "metric_date")
})
@IdClass(ProductMetricsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductMetrics {

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

    @Column(name = "unlike_count", nullable = false)
    private long unlikeCount;

    @Column(name = "sales_count", nullable = false)
    private long salesCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "cancel_count", nullable = false)
    private long cancelCount;

    @Column(name = "cancel_amount", nullable = false)
    private long cancelAmount;
}
