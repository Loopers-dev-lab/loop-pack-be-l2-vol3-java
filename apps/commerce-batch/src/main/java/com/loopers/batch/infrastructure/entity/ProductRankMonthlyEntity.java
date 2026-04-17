package com.loopers.batch.infrastructure.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "mv_product_rank_monthly",
        uniqueConstraints = @UniqueConstraint(columnNames = {"productId", "yearMonth"}))
public class ProductRankMonthlyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false, length = 7)
    private String yearMonth;

    @Column(nullable = false)
    private long likeCount;

    @Column(nullable = false)
    private long orderCount;

    @Column(nullable = false)
    private long viewCount;

    @Column(nullable = false)
    private long totalSalesAmount;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private int ranking;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public ProductRankMonthlyEntity(Long productId, String yearMonth,
                                    long likeCount, long orderCount, long viewCount,
                                    long totalSalesAmount, double score, int ranking) {
        this.productId = productId;
        this.yearMonth = yearMonth;
        this.likeCount = likeCount;
        this.orderCount = orderCount;
        this.viewCount = viewCount;
        this.totalSalesAmount = totalSalesAmount;
        this.score = score;
        this.ranking = ranking;
        this.updatedAt = LocalDateTime.now();
    }

    public void updateRanking(long likeCount, long orderCount, long viewCount,
                              long totalSalesAmount, double score, int ranking) {
        this.likeCount = likeCount;
        this.orderCount = orderCount;
        this.viewCount = viewCount;
        this.totalSalesAmount = totalSalesAmount;
        this.score = score;
        this.ranking = ranking;
        this.updatedAt = LocalDateTime.now();
    }
}
