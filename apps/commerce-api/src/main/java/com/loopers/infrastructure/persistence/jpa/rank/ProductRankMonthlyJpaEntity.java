package com.loopers.infrastructure.persistence.jpa.rank;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 월간 상품 랭킹 JPA 엔티티.
 */
@Entity
@Table(
    name = "mv_product_rank_monthly",
    indexes = {
        @Index(name = "idx_monthly_period_rank", columnList = "period_start_date, rank_number"),
        @Index(name = "idx_monthly_product", columnList = "product_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_monthly_period_product", columnNames = {"period_start_date", "product_id"})
    }
)
public class ProductRankMonthlyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_number", nullable = false)
    private int rankNumber;

    @Column(name = "total_score", nullable = false, precision = 15, scale = 4)
    private BigDecimal totalScore;

    @Column(name = "total_view_count", nullable = false)
    private long totalViewCount;

    @Column(name = "total_like_count", nullable = false)
    private long totalLikeCount;

    @Column(name = "total_order_count", nullable = false)
    private long totalOrderCount;

    @Column(name = "period_start_date", nullable = false)
    private LocalDate periodStartDate;

    @Column(name = "period_end_date", nullable = false)
    private LocalDate periodEndDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected ProductRankMonthlyJpaEntity() {}

    public ProductRankMonthlyJpaEntity(
        Long productId,
        int rankNumber,
        BigDecimal totalScore,
        long totalViewCount,
        long totalLikeCount,
        long totalOrderCount,
        LocalDate periodStartDate,
        LocalDate periodEndDate
    ) {
        this.productId = productId;
        this.rankNumber = rankNumber;
        this.totalScore = totalScore;
        this.totalViewCount = totalViewCount;
        this.totalLikeCount = totalLikeCount;
        this.totalOrderCount = totalOrderCount;
        this.periodStartDate = periodStartDate;
        this.periodEndDate = periodEndDate;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public int getRankNumber() {
        return rankNumber;
    }

    public BigDecimal getTotalScore() {
        return totalScore;
    }

    public long getTotalViewCount() {
        return totalViewCount;
    }

    public long getTotalLikeCount() {
        return totalLikeCount;
    }

    public long getTotalOrderCount() {
        return totalOrderCount;
    }

    public LocalDate getPeriodStartDate() {
        return periodStartDate;
    }

    public LocalDate getPeriodEndDate() {
        return periodEndDate;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}