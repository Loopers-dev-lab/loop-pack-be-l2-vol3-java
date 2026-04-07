package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.time.ZonedDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product_metrics", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"product_id", "metric_hour"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class ProductMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private LocalDateTime metricHour;

    @Column(nullable = false)
    private Long likeCount;

    @Column(nullable = false)
    private Long orderCount;

    @Column(nullable = false)
    private Long viewCount;

    @Column(nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
