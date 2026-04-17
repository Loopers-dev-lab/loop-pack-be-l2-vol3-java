package com.loopers.domain.ranking.mv;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 롤링 30일 랭킹 확정 MV. {@link MvProductRankLast7d} 주석 참고.
 */
@Entity
@Table(
        name = "mv_product_rank_last_30d",
        indexes = @Index(
                name = "idx_last_30d_rank",
                columnList = "anchor_date, weight_group, rank_position"
        )
)
@IdClass(MvProductRankId.class)
@Getter
public class MvProductRankLast30d {

    @Id
    @Column(name = "anchor_date", nullable = false)
    private LocalDate anchorDate;

    @Id
    @Column(name = "weight_group", length = 32, nullable = false)
    private String weightGroup;

    @Id
    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "sales_amount", nullable = false)
    private long salesAmount;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "rank_position", nullable = false)
    private int rankPosition;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected MvProductRankLast30d() {
    }

    public MvProductRankLast30d(LocalDate anchorDate, String weightGroup, Long productId,
                                long viewCount, long likeCount, long salesAmount,
                                double score, int rankPosition, LocalDateTime createdAt) {
        this.anchorDate = anchorDate;
        this.weightGroup = weightGroup;
        this.productId = productId;
        this.viewCount = viewCount;
        this.likeCount = likeCount;
        this.salesAmount = salesAmount;
        this.score = score;
        this.rankPosition = rankPosition;
        this.createdAt = createdAt;
    }
}
