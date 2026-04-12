package com.loopers.batch.job.rank.step;

import java.math.BigDecimal;

public record AggregatedScoreRow(
        long productDbId,
        double totalScore,
        long totalView,
        long totalLike,
        BigDecimal totalOrder
) {}
