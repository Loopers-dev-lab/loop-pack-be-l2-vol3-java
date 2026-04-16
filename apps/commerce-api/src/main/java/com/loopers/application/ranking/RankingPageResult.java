package com.loopers.application.ranking;

import java.time.ZonedDateTime;
import java.util.List;

public record RankingPageResult(
        List<RankingInfo> items,
        long page,
        long size,
        long totalElements,
        ZonedDateTime lastUpdatedAt,
        String periodKey,
        boolean isFallback,
        Long publishedVersion
) {
    public RankingPageResult(List<RankingInfo> items, long page, long size, long totalElements, ZonedDateTime lastUpdatedAt) {
        this(items, page, size, totalElements, lastUpdatedAt, null, false, null);
    }

    public RankingPageResult(List<RankingInfo> items, long page, long size, long totalElements,
                              ZonedDateTime lastUpdatedAt, String periodKey, boolean isFallback) {
        this(items, page, size, totalElements, lastUpdatedAt, periodKey, isFallback, null);
    }
}
