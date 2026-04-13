package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingDto;

import java.math.BigDecimal;
import java.util.List;

public class RankingV1Dto {

    public record RankingItemResponse(
        long rank,
        double score,
        Long productId,
        String productName,
        String brandName,
        BigDecimal price
    ) {
        public static RankingItemResponse from(RankingDto.RankingItemInfo info) {
            return new RankingItemResponse(
                info.rank(),
                info.score(),
                info.productId(),
                info.productName(),
                info.brandName(),
                info.price()
            );
        }
    }

    public record RankingPageResponse(
        List<RankingItemResponse> content,
        int page,
        int size
    ) {
        public static RankingPageResponse of(List<RankingDto.RankingItemInfo> items, int page, int size) {
            return new RankingPageResponse(
                items.stream().map(RankingItemResponse::from).toList(),
                page,
                size
            );
        }
    }
}
