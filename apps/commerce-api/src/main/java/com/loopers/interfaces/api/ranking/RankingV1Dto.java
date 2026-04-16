package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(
        int rank,
        Long productId,
        String name,
        String description,
        int price,
        String sellingStatus,
        double score
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                info.rank(),
                info.productId(),
                info.name(),
                info.description(),
                info.price(),
                info.sellingStatus().name(),
                info.score()
            );
        }
    }

    public record RankingPageResponse(
        List<RankingResponse> content,
        String period,
        String date,
        int page,
        int size
    ) {
    }
}
