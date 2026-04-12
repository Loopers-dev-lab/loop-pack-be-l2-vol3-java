package com.loopers.application.ranking;

import java.time.ZonedDateTime;
import java.util.List;

public record RankingPageResult(
        List<RankingInfo> items,
        long page,
        long size,
        long totalElements,
        ZonedDateTime lastUpdatedAt
) {
}
