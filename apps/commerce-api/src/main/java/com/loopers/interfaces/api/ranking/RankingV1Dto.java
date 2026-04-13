package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
            Long rank,
            Long productId,
            String productName,
            String imageUrl,
            int price,
            Long brandId,
            String brandName
    ) {
        public static RankingItemResponse from(RankingInfo info) {
            return new RankingItemResponse(
                    info.rank(),
                    info.productId(),
                    info.productName(),
                    info.imageUrl(),
                    info.price(),
                    info.brandId(),
                    info.brandName()
            );
        }
    }

    public record RankingListResponse(
            String date,
            int page,
            int size,
            String period,
            List<RankingItemResponse> rankings
    ) {
        public static RankingListResponse of(String date, int page, int size, String period, List<RankingInfo> infos) {
            return new RankingListResponse(
                    date,
                    page,
                    size,
                    period,
                    infos.stream().map(RankingItemResponse::from).toList()
            );
        }
    }
}
