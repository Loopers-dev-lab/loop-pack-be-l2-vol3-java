package com.loopers.application.ranking;

import java.util.List;

public record RankingResult(
        List<RankingItem> items,
        int page,
        int size,
        long totalElements
) {}
