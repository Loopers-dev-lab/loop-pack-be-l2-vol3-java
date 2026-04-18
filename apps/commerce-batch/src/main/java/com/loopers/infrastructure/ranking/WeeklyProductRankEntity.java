package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductRankMv;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "mv_product_rank_weekly",
    uniqueConstraints = @UniqueConstraint(name = "uk_product_year_week", columnNames = {"product_id", "year_week"}),
    indexes = @Index(name = "idx_year_week_rank", columnList = "year_week, rank")
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WeeklyProductRankEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "year_week", nullable = false, length = 8)
    private String yearWeek;

    @Column(name = "like_count", nullable = false)
    private Long likeCount;

    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    @Column(name = "sales_count", nullable = false)
    private Long salesCount;

    @Column(name = "sales_amount", nullable = false)
    private Long salesAmount;

    @Column(name = "score", nullable = false)
    private Double score;

    @Column(name = "`rank`", nullable = false)
    private Integer rank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    private void onPersist() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    public static WeeklyProductRankEntity from(ProductRankMv mv) {
        WeeklyProductRankEntity entity = new WeeklyProductRankEntity();
        entity.productId = mv.getProductId();
        entity.yearWeek = mv.getPeriodKey();
        entity.likeCount = mv.getLikeCount();
        entity.viewCount = mv.getViewCount();
        entity.salesCount = mv.getSalesCount();
        entity.salesAmount = mv.getSalesAmount();
        entity.score = mv.getScore();
        entity.rank = mv.getRank();
        return entity;
    }
}
