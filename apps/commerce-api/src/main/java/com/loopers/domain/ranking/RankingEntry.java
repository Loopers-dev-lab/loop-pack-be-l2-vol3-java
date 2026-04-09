package com.loopers.domain.ranking;

public record RankingEntry(
        Long productDbId,
        double score
) {
}
