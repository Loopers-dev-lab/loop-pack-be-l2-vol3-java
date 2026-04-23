package com.loopers.domain.ranking;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(name = "uk_weekly_product_yearweek", columnNames = {"product_id", "year_week"}),
    indexes = @Index(name = "idx_weekly_yearweek_rank", columnList = "year_week, rank_number")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRankWeeklyModel extends BaseEntity {

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_number", nullable = false)
    private int rankNumber;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_quantity", nullable = false)
    private long salesQuantity;

    @Column(name = "year_week", nullable = false, length = 8)
    private String yearWeek;

    public ProductRankWeeklyModel(Long productId, int rankNumber, double score,
                                  long viewCount, long likeCount, long salesQuantity,
                                  String yearWeek) {
        this.productId = productId;
        this.rankNumber = rankNumber;
        this.score = score;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesQuantity = salesQuantity;
        this.yearWeek = yearWeek;
    }
}
