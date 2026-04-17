package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.ranking.RankingQueryUseCase.RankingItemInfo;

public record RankingItemResponse(
        long rank,
        double score,
        Long productId,
        Long brandId,
        String brandName,
        String productName,
        int price,
        Integer salePrice,
        boolean onSale,
        int likeCount
) {
    public static RankingItemResponse from(RankingItemInfo info) {
        return new RankingItemResponse(
                info.rank(),
                info.score(),
                info.productId(),
                info.brandId(),
                info.brandName(),
                info.productName(),
                info.price(),
                info.salePrice(),
                info.onSale(),
                info.likeCount()
        );
    }
}
