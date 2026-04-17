package com.loopers.application.ranking.dto;

import com.loopers.domain.product.model.Product;
import com.loopers.domain.ranking.model.RankingEntry;

public record FindRankingItemResDto(
        long rank,
        double score,
        Long productId,
        String productName,
        int price
) {
    public static FindRankingItemResDto from(RankingEntry entry, Product product) {
        return new FindRankingItemResDto(
                entry.rank(),
                entry.score(),
                entry.productId(),
                product.getName().value(),
                product.getPrice().value()
        );
    }
}
