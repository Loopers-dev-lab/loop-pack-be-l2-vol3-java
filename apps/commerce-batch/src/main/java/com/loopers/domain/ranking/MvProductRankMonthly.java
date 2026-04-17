package com.loopers.domain.ranking;

import lombok.Getter;

import java.time.LocalDate;

@Getter
public class MvProductRankMonthly {

    private final Long productId;
    private final int rank;
    private final double score;
    private final long totalLike;
    private final long totalOrder;
    private final long totalView;
    private final long totalSales;
    private final LocalDate baseDate;

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
}
