package com.loopers.batch.ranking;

import java.math.BigDecimal;

public record RankingAggregateRow(
    long productId,
    long viewCount,
    long likeCount,
    BigDecimal orderRevenue,
    double score
) {}
