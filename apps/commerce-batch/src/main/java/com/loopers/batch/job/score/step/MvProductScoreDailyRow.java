package com.loopers.batch.job.score.step;

import java.math.BigDecimal;
import java.time.LocalDate;

public record MvProductScoreDailyRow(
        long productDbId,
        LocalDate scoreDate,
        double score,
        long viewCount,
        long likeCount,
        BigDecimal orderAmount
) {}
