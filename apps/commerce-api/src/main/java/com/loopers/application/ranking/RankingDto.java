package com.loopers.application.ranking;

import com.loopers.application.product.ProductDto;

import java.math.BigDecimal;

public class RankingDto {

    public record RankingItemInfo(
        long rank,
        double score,
        Long productId,
        String productName,
        String brandName,
        BigDecimal price
    ) {
        public static RankingItemInfo of(long rank, double score, ProductDto.ProductInfo product) {
            return new RankingItemInfo(
                rank,
                score,
                product.id(),
                product.name(),
                product.brandName(),
                product.price()
            );
        }
    }

    public record ProductRankInfo(
        Long rank,
        Double score
    ) {
    }
}
