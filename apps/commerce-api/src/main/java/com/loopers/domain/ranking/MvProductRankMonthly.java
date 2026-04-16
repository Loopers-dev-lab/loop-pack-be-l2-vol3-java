package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 월간 랭킹 Materialized View (mv_product_rank_monthly).
 * Spring Batch MonthlyRankingJob 이 집계한 결과를 조회 전용으로 사용한다.
 */
@Getter
@Entity
@Table(name = "mv_product_rank_monthly")
public class MvProductRankMonthly {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "ranking_period", nullable = false)
    private String rankingPeriod;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MvProductRankMonthly() {
    }
}
