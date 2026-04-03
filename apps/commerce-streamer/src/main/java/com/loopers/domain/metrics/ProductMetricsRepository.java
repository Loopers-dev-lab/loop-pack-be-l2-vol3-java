package com.loopers.domain.metrics;

import java.math.BigDecimal;

public interface ProductMetricsRepository {

    void incrementLikeCount(Long productId, long delta);

    void incrementViewCount(Long productId, long delta);

    void incrementSales(Long productId, long countDelta, BigDecimal amountDelta);
}
