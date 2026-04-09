package com.loopers.application.ranking;

public record ProductRankInfo(
        Long productId,
        Long rank,
        Double score,
        String date
) {}
