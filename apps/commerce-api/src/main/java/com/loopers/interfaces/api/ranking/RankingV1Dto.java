package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingWithProduct;

import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(
        long rank,
        double score,
        Long productId,
        String productName,
        int productPrice,
        String brandName
    ) {
        public static RankingResponse from(RankingWithProduct rwp) {
            return new RankingResponse(
                rwp.rank(), rwp.score(),
                rwp.productId(), rwp.productName(),
                rwp.productPrice(), rwp.brandName()
            );
        }
    }

    public record RankingListResponse(
        String date,
        int page,
        int size,
        String period,
        List<RankingResponse> rankings
    ) {}
}
