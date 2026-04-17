package com.loopers.domain.ranking;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "mv_product_rank_monthly")
public class MvProductRankMonthly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "`rank`", nullable = false)
    private int rank;

    @Column(name = "score", nullable = false)
    private double score;

    @Column(name = "total_like", nullable = false)
    private long totalLike;

    @Column(name = "total_order", nullable = false)
    private long totalOrder;

    @Column(name = "total_view", nullable = false)
    private long totalView;

    @Column(name = "total_sales", nullable = false)
    private long totalSales;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    private MvProductRankMonthly(Long productId, int rank, double score,
                                  long totalLike, long totalOrder, long totalView,
                                  long totalSales, LocalDate baseDate) {
        this.productId = productId;
        this.rank = rank;
        this.score = score;
        this.totalLike = totalLike;
        this.totalOrder = totalOrder;
        this.totalView = totalView;
        this.totalSales = totalSales;
        this.baseDate = baseDate;
    }

    public static MvProductRankMonthly of(Long productId, int rank, double score,
                                           long totalLike, long totalOrder, long totalView,
                                           long totalSales, LocalDate baseDate) {
        return new MvProductRankMonthly(productId, rank, score, totalLike, totalOrder, totalView, totalSales, baseDate);
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
    }
}
