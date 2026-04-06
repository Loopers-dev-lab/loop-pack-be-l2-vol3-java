package com.loopers.interfaces.api.ranking;

import com.loopers.application.product.ProductReadModel;
import com.loopers.domain.PageResult;
import com.loopers.domain.ranking.ProductRanking;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class RankingV1Dto {

    public record RankingProductResponse(
        Long productId,
        String productName,
        int price,
        long rank
    ) {
        public static RankingProductResponse from(ProductRanking ranking, ProductReadModel product) {
            Objects.requireNonNull(product, "product must not be null");
            return new RankingProductResponse(
                ranking.productId(),
                product.name(),
                product.price(),
                ranking.rank()
            );
        }
    }

    public record RankingPageResponse(
        List<RankingProductResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
    ) {
        public static RankingPageResponse from(
            PageResult<ProductRanking> rankings,
            Map<Long, ProductReadModel> productMap
        ) {
            List<RankingProductResponse> content = rankings.items().stream()
                .filter(ranking -> productMap.containsKey(ranking.productId()))
                .map(ranking -> RankingProductResponse.from(
                    ranking, productMap.get(ranking.productId())
                ))
                .toList();
            return new RankingPageResponse(
                content, rankings.page(), rankings.size(),
                rankings.totalElements(), rankings.totalPages()
            );
        }
    }
}
