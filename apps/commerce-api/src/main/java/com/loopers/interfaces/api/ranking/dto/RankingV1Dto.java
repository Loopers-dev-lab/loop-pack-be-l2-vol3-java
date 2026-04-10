package com.loopers.interfaces.api.ranking.dto;

import com.loopers.application.ranking.RankingProductInfo;

public class RankingV1Dto {

    public record RankingResponse(
            Long productId,
            String productName,
            Integer price,
            String brandName,
            Long rank,
            Double score
    ) {
        public static RankingResponse from(RankingProductInfo info) {
            return new RankingResponse(
                    info.productId(),
                    info.productName(),
                    info.price(),
                    info.brandName(),
                    info.rank(),
                    info.score()
            );
        }
    }
}
