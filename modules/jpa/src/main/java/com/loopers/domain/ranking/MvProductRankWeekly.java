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
        name = "mv_product_rank_weekly",
        indexes = @Index(name = "idx_weekly_rank", columnList = "ranking_week, ranking")
)
@IdClass(MvProductRankWeeklyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankWeekly {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "ranking_week", length = 8)
    private String yearWeek;

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

    public static MvProductRankWeekly create(
            Long productId,
            String yearWeek,
            long viewCount,
            long likeCount,
            long orderAmount,
            double score,
            int ranking
    ) {
        MvProductRankWeekly entity = new MvProductRankWeekly();
        entity.productId = productId;
        entity.yearWeek = yearWeek;
        entity.viewCount = viewCount;
        entity.likeCount = likeCount;
        entity.orderAmount = orderAmount;
        entity.score = score;
        entity.ranking = ranking;
        entity.updatedAt = ZonedDateTime.now();
        return entity;
    }
}
