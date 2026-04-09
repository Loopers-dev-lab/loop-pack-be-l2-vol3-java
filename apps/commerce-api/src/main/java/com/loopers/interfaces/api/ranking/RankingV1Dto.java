package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

public class RankingV1Dto {

    public record RankingResponse(
        long rank,
        Long productId,
        String productName,
        Long price,
        String brandName,
        double score
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                info.rank(),
                info.productId(),
                info.productName(),
                info.price(),
                info.brandName(),
                info.score()
            );
        }
    }
}
