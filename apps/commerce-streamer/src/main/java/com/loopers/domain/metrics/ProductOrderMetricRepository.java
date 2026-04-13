package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.util.Map;

public interface ProductOrderMetricRepository {

    void upsert(Long productId, LocalDateTime bucketTime, int orderDelta, long qtyDelta, long amountDelta);

    // Query
    Map<Long, Long> sumQuantityByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit);

    Map<Long, Long> sumSalesAmountByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit);
}
