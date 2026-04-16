package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mv_product_rank_weekly")
@IdClass(MvProductRankWeeklyId.class)
public class MvProductRankWeekly {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "week_start_date")
    private LocalDate weekStartDate;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_count", nullable = false)
    private long orderCount;

    @Column(name = "total_score", nullable = false)
    private double totalScore;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "aggregated_at", nullable = false)
    private LocalDateTime aggregatedAt;

    protected MvProductRankWeekly() {}

    public static MvProductRankWeekly of(
        Long productId, LocalDate weekStartDate, double totalScore,
        long viewCount, long likeCount, long orderCount, int rankPosition
    ) {
        MvProductRankWeekly entity = new MvProductRankWeekly();
        entity.productId = productId;
        entity.weekStartDate = weekStartDate;
        entity.viewCount = viewCount;
        entity.likeCount = likeCount;
        entity.orderCount = orderCount;
        entity.totalScore = totalScore;
        entity.rankPosition = rankPosition;
        entity.aggregatedAt = LocalDateTime.now();
        return entity;
    }

    public Long getProductId() { return productId; }
    public LocalDate getWeekStartDate() { return weekStartDate; }
    public long getViewCount() { return viewCount; }
    public long getLikeCount() { return likeCount; }
    public long getOrderCount() { return orderCount; }
    public double getTotalScore() { return totalScore; }
    public int getRankPosition() { return rankPosition; }
    public LocalDateTime getAggregatedAt() { return aggregatedAt; }
}
