package com.loopers.domain.metrics;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "product_daily_metrics",
        uniqueConstraints = @UniqueConstraint(columnNames = {"product_id", "metric_date"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductDailyMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metric_date", nullable = false)
    private LocalDate metricDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
