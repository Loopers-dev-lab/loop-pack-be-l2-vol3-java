package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingInfo;

import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
        Long rank,
        Long productId,
        String name,
        String brandName,
        Integer price,
        Integer stock,
        Integer likesCount,
        String description,
        String imageUrl
    ) {
        public static RankingItemResponse from(RankingInfo info) {
            return new RankingItemResponse(
                info.rank(),
                info.productId(),
                info.name(),
                info.brandName(),
                info.price(),
                info.stock(),
                info.likesCount(),
                info.description(),
                info.imageUrl()
            );
        }
    }

    public record RankingPageResponse(
        List<RankingItemResponse> content,
        long totalElements,
        int page,
        int size
    ) {}
}
