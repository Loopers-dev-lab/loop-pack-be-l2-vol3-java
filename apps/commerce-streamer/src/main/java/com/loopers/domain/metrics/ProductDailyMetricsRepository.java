package com.loopers.domain.metrics;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public interface ProductDailyMetricsRepository {
    void upsertViewCount(Long productId, LocalDate date, ZonedDateTime updatedAt);
    void upsertLikeCount(Long productId, LocalDate date, int delta, ZonedDateTime updatedAt);
    void upsertOrderAmount(Long productId, LocalDate date, long amount, ZonedDateTime updatedAt);
    List<ProductDailyMetrics> findByMetricDate(LocalDate date);

    void bulkUpsert(List<DailyDelta> deltas);

    record DailyDelta(Long productId, LocalDate date, long viewDelta, long likeDelta, long orderAmountDelta, ZonedDateTime updatedAt) {}
}
