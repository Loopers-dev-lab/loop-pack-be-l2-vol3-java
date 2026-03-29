package com.loopers.domain.metrics;

import java.time.ZonedDateTime;

public interface ProductMetricsRepository {

    void incrementLikeCount(Long productId, ZonedDateTime occurredAt);

    void decrementLikeCount(Long productId, ZonedDateTime occurredAt);

    void incrementSalesCount(Long productId, ZonedDateTime occurredAt);

    void incrementViewCount(Long productId, ZonedDateTime occurredAt);
}