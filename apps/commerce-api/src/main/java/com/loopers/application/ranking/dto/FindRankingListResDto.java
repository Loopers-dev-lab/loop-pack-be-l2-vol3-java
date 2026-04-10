package com.loopers.application.ranking.dto;

import java.util.List;

public record FindRankingListResDto(
        List<FindRankingItemResDto> rankings,
        long totalCount,
        int page,
        int size
) {
}
