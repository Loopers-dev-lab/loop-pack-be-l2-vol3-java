package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;
import com.loopers.domain.ranking.RankingPeriod;

import java.time.LocalDate;
import java.util.List;

public record RankingInfo(
        RankingPeriod period,
        LocalDate date,
        int page,
        int size,
        long totalCount,
        List<RankingItem> items
) {

    public record RankingItem(
            int rank,
            double score,
            ProductInfo product
    ) {
    }
}
