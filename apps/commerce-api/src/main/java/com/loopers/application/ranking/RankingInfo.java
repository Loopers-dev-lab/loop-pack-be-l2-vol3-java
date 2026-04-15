package com.loopers.application.ranking;

import com.loopers.domain.product.Product;
import com.loopers.domain.ranking.ProductRankingInfo;

public record RankingInfo(
    Long rank,
    Double score,
    Long productId,
    String productName,
    String productCode,
    Long basePrice,
    Long discountedPrice,
    String brandName
) {
    public static RankingInfo from(ProductRankingInfo rankingInfo, Product product, String brandName) {
        return new RankingInfo(
            rankingInfo.rank(),
            rankingInfo.score(),
            rankingInfo.productId(),
            product.getName(),
            product.getProductCode(),
            product.getBasePrice(),
            product.calculateDiscountedPrice(),
            brandName
        );
    }
}
