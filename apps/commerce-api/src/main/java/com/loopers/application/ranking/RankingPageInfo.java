package com.loopers.application.ranking;

import java.time.LocalDate;
import java.util.List;

public record RankingPageInfo(
    LocalDate date,
    int page,
    int size,
    long totalElements,
    int totalPages,
    List<RankedProductInfo> content
) {
}
