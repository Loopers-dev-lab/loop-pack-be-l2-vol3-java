package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingResponse(
            int rank,
            Long productId,
            String name,
            Long price,
            double score
    ) {
        public static RankingResponse from(RankingInfo info) {
            return new RankingResponse(
                    info.rank(),
                    info.productId(),
                    info.name(),
                    info.price(),
                    info.score()
            );
        }
    }

    public record RankingListResponse(List<RankingResponse> rankings) {
        public static RankingListResponse from(List<RankingInfo> infos) {
            List<RankingResponse> rankings = infos.stream()
                    .map(RankingResponse::from)
                    .toList();
            return new RankingListResponse(rankings);
        }
    }
}
