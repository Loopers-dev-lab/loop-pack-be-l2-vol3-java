package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 주간 TOP 100 랭킹 Materialized View.
 * 배치 Job이 product_metrics에서 가중치 점수를 계산하여 적재한다.
 * 흐름: product_metrics → Batch(Reader/Processor/Writer) → 이 테이블 → API 조회
 */
@Entity
@Table(name = "mv_product_rank_weekly", indexes = {
        @Index(name = "idx_mv_weekly_year_week_ranking", columnList = "year_week, `ranking`")
})
@Getter
public class MvProductRankWeeklyModel extends BaseEntity {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private double score;       // viewCount * 0.1 + likeCount * 0.2 + salesCount * 0.7

    @Column(name = "`ranking`", nullable = false)
    private int ranking;        // 1-based 순위

    @Column(name = "year_week", nullable = false, length = 10)
    private String yearWeek;    // e.g., "2026W15"

    protected MvProductRankWeeklyModel() {}

    public MvProductRankWeeklyModel(Long productId, long viewCount, long likeCount,
                                    long salesCount, double score, int ranking, String yearWeek) {
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.score = score;
        this.ranking = ranking;
        this.yearWeek = yearWeek;
    }
}
