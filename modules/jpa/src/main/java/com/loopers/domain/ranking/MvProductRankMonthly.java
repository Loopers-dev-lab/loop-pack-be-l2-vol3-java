package com.loopers.domain.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "mv_product_rank_monthly",
        indexes = @Index(name = "idx_monthly_rank", columnList = "ranking_month, ranking")
)
@IdClass(MvProductRankMonthlyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthly {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "ranking_month", length = 7)
    private String yearMonth;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "like_count", nullable = false)
    private long likeCount;

    @Column(name = "order_amount", nullable = false)
    private long orderAmount;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "ranking", nullable = false)
    private int ranking;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public static MvProductRankMonthly create(
            Long productId,
            String yearMonth,
            long viewCount,
            long likeCount,
            long orderAmount,
            double score,
            int ranking
    ) {
        MvProductRankMonthly entity = new MvProductRankMonthly();
        entity.productId = productId;
        entity.yearMonth = yearMonth;
        entity.viewCount = viewCount;
        entity.likeCount = likeCount;
        entity.orderAmount = orderAmount;
        entity.score = score;
        entity.ranking = ranking;
        entity.updatedAt = ZonedDateTime.now();
        return entity;
    }
}
