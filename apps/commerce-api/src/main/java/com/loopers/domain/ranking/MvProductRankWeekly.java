package com.loopers.domain.ranking;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "mv_product_rank_weekly")
public class MvProductRankWeekly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_position", nullable = false)
    private long rankPosition;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MvProductRankWeekly() {}

    public static MvProductRankWeekly of(Long productId, double score, long rankPosition,
                                          LocalDate periodStart, LocalDate periodEnd) {
        MvProductRankWeekly entity = new MvProductRankWeekly();
        entity.productId = productId;
        entity.score = score;
        entity.rankPosition = rankPosition;
        entity.periodStart = periodStart;
        entity.periodEnd = periodEnd;
        entity.updatedAt = LocalDateTime.now();
        return entity;
    }

    public Long getId() { return id; }
    public Long getProductId() { return productId; }
    public double getScore() { return score; }
    public long getRankPosition() { return rankPosition; }
    public LocalDate getPeriodStart() { return periodStart; }
    public LocalDate getPeriodEnd() { return periodEnd; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
