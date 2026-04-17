package com.loopers.batch.infrastructure.metrics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "product_metrics_daily")
@IdClass(ProductMetricsDailyEntity.ProductMetricsDailyId.class)
public class ProductMetricsDailyEntity {

    @Id
    @Column(nullable = false)
    private LocalDate metricDate;

    @Id
    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public record ProductMetricsDailyId(LocalDate metricDate, Long productId) implements Serializable {
    }
}
