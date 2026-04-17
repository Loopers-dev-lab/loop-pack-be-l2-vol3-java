package com.loopers.domain.ranking.model;

import java.util.List;

public record RankingSlice(
        List<RankingEntry> entries,
        long totalCount
) {
}
