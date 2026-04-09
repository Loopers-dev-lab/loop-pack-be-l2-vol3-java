package com.loopers.domain.metrics;

import java.time.LocalDateTime;

public interface ProductOrderMetricRepository {

    void upsert(Long productId, LocalDateTime bucketTime, int orderDelta, long qtyDelta, long amountDelta);
}
