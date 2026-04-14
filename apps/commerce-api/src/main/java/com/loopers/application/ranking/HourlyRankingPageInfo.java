package com.loopers.application.ranking;

import java.time.LocalDateTime;
import java.util.List;

public record HourlyRankingPageInfo(
    LocalDateTime hour,
    int page,
    int size,
    long totalElements,
    int totalPages,
    List<RankedProductInfo> content
) {
}
