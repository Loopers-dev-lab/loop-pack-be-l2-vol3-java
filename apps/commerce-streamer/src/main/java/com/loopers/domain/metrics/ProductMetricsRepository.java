package com.loopers.domain.metrics;

import java.time.ZonedDateTime;
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
}
