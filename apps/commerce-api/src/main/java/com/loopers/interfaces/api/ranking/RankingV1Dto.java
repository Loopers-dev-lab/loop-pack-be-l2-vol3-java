package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

public class RankingV1Dto {

    public record RankingResponse(
        Long rank,
        Double score,
        Long productId,
        String productName,
        String productCode,
        Long basePrice,
        Long discountedPrice,
        String brandName
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                info.rank(),
                info.score(),
                info.productId(),
                info.productName(),
                info.productCode(),
                info.basePrice(),
                info.discountedPrice(),
                info.brandName()
            );
        }
    }
}
