package com.loopers.domain.rank;

import java.math.BigDecimal;

public record MvProductRankRow(
        String periodKey,
        int rankNo,
        long refProductId,
        double score,
        long viewCount,
        long likeCount,
        BigDecimal orderAmount
) {}
