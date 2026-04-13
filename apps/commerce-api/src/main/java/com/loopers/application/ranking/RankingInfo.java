package com.loopers.application.ranking;

import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;

public record RankingInfo(
        int rank,
        double score,
        Long productId,
        String productName,
        Money basePrice
) {
    public static RankingInfo of(RankingEntry entry, Product product) {
        return new RankingInfo(
                entry.rank(),
                entry.score(),
                product.getId(),
                product.getName(),
                product.getBasePrice()
        );
    }
}
