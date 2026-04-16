package com.loopers.domain.ranking;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(
    name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(name = "uq_product_week", columnNames = {"product_id", "period_week"})
)
@Getter
public class ProductRankWeekly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "rank_position", nullable = false)
    private Integer rankPosition;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "order_count", nullable = false)
    private Long orderCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "period_week", nullable = false, length = 10)
    private String yearWeek;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ProductRankWeekly() {}

    public static ProductRankWeekly of(Long productId, double score, long likeCount, long orderCount, long viewCount, String yearWeek) {
        ProductRankWeekly entity = new ProductRankWeekly();
        entity.productId = productId;
        entity.rankPosition = 0;
        entity.score = score;
        entity.likeCount = likeCount;
        entity.orderCount = orderCount;
        entity.viewCount = viewCount;
        entity.yearWeek = yearWeek;
        entity.updatedAt = LocalDateTime.now();
        return entity;
    }

    public void assignRank(int rank) {
        this.rankPosition = rank;
    }
}
