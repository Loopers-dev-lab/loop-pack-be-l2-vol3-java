package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "mv_product_rank_monthly", indexes = {
        @Index(name = "idx_mv_monthly_year_month_ranking", columnList = "`year_month`, `ranking`")
})
@Getter
public class MvProductRankMonthlyModel extends BaseEntity {

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long salesCount;

    @Column(nullable = false)
    private double score;

    @Column(name = "`ranking`", nullable = false)
    private int ranking;

    @Column(name = "`year_month`", nullable = false, length = 10)
    private String yearMonth;   // e.g., "202604"

    protected MvProductRankMonthlyModel() {}

    public MvProductRankMonthlyModel(Long productId, long viewCount, long likeCount,
                                     long salesCount, double score, int ranking, String yearMonth) {
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesCount = salesCount;
        this.score = score;
        this.ranking = ranking;
        this.yearMonth = yearMonth;
    }
}
