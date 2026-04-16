package com.loopers.application.ranking;

import java.time.ZonedDateTime;
import java.util.List;

public record MvRankStatusInfo(
        String periodType,
        String periodKey,
        long publishedVersion,
        long nextVersion,
        ZonedDateTime updatedAt,
        long totalRowCount,
        long publishedRowCount,
        long orphanRowCount,
        List<MvRankVersionCount> versionBreakdown
) {
}
