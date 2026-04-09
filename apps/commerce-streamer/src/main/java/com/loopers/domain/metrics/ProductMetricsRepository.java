package com.loopers.domain.metrics;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductMetricsRepository {
    ProductMetrics save(ProductMetrics productMetrics);
    Optional<ProductMetrics> findById(Long productId);
    void insertIgnore(Long productId);
    int incrementLikeCount(Long productId, ZonedDateTime occurredAt);
    int decrementLikeCount(Long productId, ZonedDateTime occurredAt);
    int incrementViewCount(Long productId, ZonedDateTime occurredAt);
    int incrementSalesCount(Long productId, ZonedDateTime occurredAt);
    int decrementSalesCount(Long productId, ZonedDateTime occurredAt);

    void bulkInsertIgnoreProducts(Collection<Long> productIds);
    void bulkUpsertAllTime(List<AllTimeDelta> deltas);

    record AllTimeDelta(Long productId, long viewDelta, long likeDelta, long salesDelta, ZonedDateTime occurredAt) {}
}
