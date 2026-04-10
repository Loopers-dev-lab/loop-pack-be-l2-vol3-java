package com.loopers.application.ranking;

import java.util.UUID;

public record RankingProductView(
        UUID productId,
        Long rank,
        Double score
) {
}
