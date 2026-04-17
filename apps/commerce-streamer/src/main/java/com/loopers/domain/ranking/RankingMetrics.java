package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

/**
 * 랭킹 집계 테이블.
 *
 * 상품별 + 날짜별 + 시간대별(0~23) 원시 데이터를 저장한다.
 * Kafka Consumer가 이벤트를 소비하여 UPSERT하고, dirty 플래그를 true로 설정한다.
 * SyncScheduler가 dirty 행을 조회하여 Redis ZSET에 동기화한 뒤 dirty=false로 전환한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "ranking_metrics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ranking_metrics_product_date_hour",
                columnNames = {"product_id", "metrics_date", "metrics_hour"}
        ),
        indexes = @Index(
                name = "idx_ranking_metrics_dirty_date",
                columnList = "dirty, metrics_date"
        )
)
public class RankingMetrics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "metrics_date", nullable = false)
    private LocalDate metricsDate;

    @Column(name = "metrics_hour", nullable = false)
    private int metricsHour;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "order_revenue", nullable = false, precision = 15, scale = 2)
    private BigDecimal orderRevenue;

    @Column(name = "dirty", nullable = false)
    private boolean dirty;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    private void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
