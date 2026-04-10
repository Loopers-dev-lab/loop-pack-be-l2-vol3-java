package com.loopers.application.ranking;

import java.util.UUID;

public record TopRankingProductView(
        UUID productId,
        String name,
        Integer price,
        UUID brandId,
        String brandName,
        Integer likeCount,
        Long rank,
        Double score
) {
}
