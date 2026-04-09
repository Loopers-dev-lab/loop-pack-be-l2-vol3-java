package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.ProductRankInfo;
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

    public record ProductRankResponse(
            Long productId,
            Long rank,
            Double score,
            String date
    ) {
        public static ProductRankResponse from(ProductRankInfo info) {
            return new ProductRankResponse(
                    info.productId(),
                    info.rank(),
                    info.score(),
                    info.date()
            );
        }
    }
}
