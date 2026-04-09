package com.loopers.interfaces.api.ranking;

import com.loopers.application.ranking.RankingProductView;
import com.loopers.application.ranking.TopRankingProductView;

import java.util.List;
import java.util.UUID;

public class RankingDto {

    public record TopRankingResponse(
            List<TopRankingItemResponse> items
    ) {
        public static TopRankingResponse from(List<TopRankingProductView> views) {
            return new TopRankingResponse(views.stream().map(TopRankingItemResponse::from).toList());
        }
    }

    public record TopRankingItemResponse(
            UUID productId,
            String name,
            Integer price,
            UUID brandId,
            BrandInfo brand,
            Integer likeCount,
            Long rank,
            Double score
    ) {
        public static TopRankingItemResponse from(TopRankingProductView view) {
            return new TopRankingItemResponse(
                    view.productId(),
                    view.name(),
                    view.price(),
                    view.brandId(),
                    new BrandInfo(view.brandId(), view.brandName()),
                    view.likeCount(),
                    view.rank(),
                    view.score()
            );
        }
    }

    public record ProductRankResponse(
            UUID productId,
            Long rank,
            Double score
    ) {
        public static ProductRankResponse from(RankingProductView view) {
            return new ProductRankResponse(view.productId(), view.rank(), view.score());
        }
    }

    public record BrandInfo(
            UUID id,
            String name
    ) {
    }
}
