package com.loopers.application.ranking;

import java.math.BigDecimal;

record QuarterlyRankViewRow(
        int rankNo,
        long productDbId,
        double score,
        String productId,
        String productName,
        BigDecimal price
) {
}
