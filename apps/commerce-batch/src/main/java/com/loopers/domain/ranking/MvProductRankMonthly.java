package com.loopers.domain.ranking;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "mv_product_rank_monthly", uniqueConstraints = {
    @UniqueConstraint(name = "uk_product_aggregated", columnNames = {"product_id", "aggregated_at"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MvProductRankMonthly extends MvProductRank {

    public MvProductRankMonthly(Long productId, double score, int rank,
                                long viewCount, long likeCount, long salesCount,
                                LocalDate aggregatedAt) {
        super(productId, score, rank, viewCount, likeCount, salesCount, aggregatedAt);
    }

    public static MvProductRankMonthly of(ProductMetricsAggregation aggregation, int rank, LocalDate aggregatedAt) {
        return new MvProductRankMonthly(
            aggregation.getProductId(),
            aggregation.calculateScore(),
            rank,
            aggregation.getViewCount(),
            aggregation.getLikeCount(),
            aggregation.getSalesCount(),
            aggregatedAt
        );
    }
}
