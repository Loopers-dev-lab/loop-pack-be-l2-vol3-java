package com.loopers.infrastructure.ranking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "mv_product_rank_monthly")
@NoArgsConstructor
public class MvProductRankMonthlyEntity {

    @Id
    @Comment("상품 id (ref)")
    @Column(name = "product_id", nullable = false, updatable = false)
    private Long productId;

    @Comment("집계 점수")
    @Column(name = "score", nullable = false)
    private double score;

    @Comment("집계 기준 월 (e.g. 202604)")
    @Column(name = "year_month", nullable = false, length = 6)
    private String yearMonth;

    @Comment("랭킹 순위")
    @Column(name = "product_rank", nullable = false)
    private int productRank;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public MvProductRankMonthlyEntity(Long productId, double score, String yearMonth, int productRank) {
        this.productId = productId;
        this.score = score;
        this.yearMonth = yearMonth;
        this.productRank = productRank;
    }

    public void update(double score, String yearMonth, int productRank) {
        this.score = score;
        this.yearMonth = yearMonth;
        this.productRank = productRank;
    }

    @PrePersist
    private void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getProductId() {
        return productId;
    }

    public double getScore() {
        return score;
    }

    public String getYearMonth() {
        return yearMonth;
    }

    public int getProductRank() {
        return productRank;
    }
}
