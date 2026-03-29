package com.loopers.domain.metrics;

import java.math.BigDecimal;
import java.time.Instant;

public interface ProductMetricsRepository {

    void upsertViewCount(Long productId, int delta);

    void upsertLikeCount(Long productId, int delta);

    void upsertSalesCount(Long productId, int delta);

    void upsertPrice(Long productId, BigDecimal price, Instant occurredAt);
}
