package com.loopers.domain.ranking;

public record ProductAggregation(
        Long productId,
        Long totalLike,
        Long totalOrder,
        Long totalView,
        Long totalSales
) {}
