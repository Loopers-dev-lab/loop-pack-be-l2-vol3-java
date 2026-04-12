package com.loopers.application.ranking;

public record RankingInfo(
        int rank,
        Long productId,
        String name,
        Long price,
        String description,
        long likeCount,
        double score
) {}
