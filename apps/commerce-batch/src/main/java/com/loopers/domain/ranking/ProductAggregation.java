package com.loopers.domain.ranking;

public record ProductAggregation(
        Long productId,
        int rank,
        Double score,
        Long totalLike,
        Long totalOrder,
        Long totalView,
        Long totalSales
) {}
