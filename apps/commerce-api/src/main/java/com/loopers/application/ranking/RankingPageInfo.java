package com.loopers.application.ranking;

import java.util.List;

public record RankingPageInfo(
        String date,
        List<RankingInfo> items,
        long totalElements,
        int page,
        int size
) {}
