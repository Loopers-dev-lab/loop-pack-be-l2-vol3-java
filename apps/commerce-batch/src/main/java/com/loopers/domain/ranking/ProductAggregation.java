package com.loopers.domain.ranking;

public record ProductAggregation(
        Long productId,
        Double score,
        Long totalLike,
        Long totalOrder,
        Long totalView,
        Long totalSales
) {}
