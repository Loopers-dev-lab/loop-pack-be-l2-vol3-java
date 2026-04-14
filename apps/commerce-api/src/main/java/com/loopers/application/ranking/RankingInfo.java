package com.loopers.application.ranking;

import com.loopers.application.product.ProductInfo;

public record RankingInfo(Long rank, Long productId, String name, String description,
                          String brand, int price, long likeCount) {

    public static RankingInfo of(long rank, ProductInfo productInfo) {
        return new RankingInfo(rank, productInfo.productId(), productInfo.name(), productInfo.description(),
                productInfo.brand(), productInfo.price(), productInfo.likeCount());
    }
}
