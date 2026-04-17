package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingItem;
import com.loopers.application.ranking.RankingResult;

import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
            int rank,
            long productId,
            String productName,
            String brandName,
            int price,
            double score
    ) {
        public static RankingItemResponse from(RankingItem item) {
            return new RankingItemResponse(
                    item.rank(),
                    item.productInfo().id(),
                    item.productInfo().name(),
                    item.productInfo().brandName(),
                    item.productInfo().price(),
                    item.score() != null ? item.score() : 0.0
            );
        }
    }

    public record RankingListResponse(
            List<RankingItemResponse> rankings,
            int page,
            int size,
            long totalElements
    ) {
        public static RankingListResponse from(RankingResult result) {
            return new RankingListResponse(
                    result.items().stream().map(RankingItemResponse::from).toList(),
                    result.page(),
                    result.size(),
                    result.totalElements()
            );
        }
    }
}
