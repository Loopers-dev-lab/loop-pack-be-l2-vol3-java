package com.loopers.application.ranking;

import java.math.BigDecimal;

public record RankingInfo(
        long rank,
        double score,
        Long productDbId,
        String productId,
        String productName,
        BigDecimal price,
        String status
) {
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISCONTINUED = "DISCONTINUED";
}
