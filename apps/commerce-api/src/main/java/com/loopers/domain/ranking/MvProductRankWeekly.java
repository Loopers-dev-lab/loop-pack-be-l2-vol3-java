package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 주간 랭킹 Materialized View (mv_product_rank_weekly).
 * Spring Batch WeeklyRankingJob 이 집계한 결과를 조회 전용으로 사용한다.
 */
@Getter
@Entity
@Table(name = "mv_product_rank_weekly")
public class MvProductRankWeekly {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    @Column(name = "order_count", nullable = false)
    private int orderCount;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "year_month_week", nullable = false)
    private String yearMonthWeek;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected MvProductRankWeekly() {
    }
}
