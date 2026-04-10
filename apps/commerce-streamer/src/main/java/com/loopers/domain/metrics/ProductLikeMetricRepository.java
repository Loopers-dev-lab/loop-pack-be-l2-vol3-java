package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.util.Map;

public interface ProductLikeMetricRepository {

    void upsert(Long productId, LocalDateTime bucketTime, int delta);

    // Query
    Map<Long, Long> sumByBucketTimeRange(LocalDateTime from, LocalDateTime to, int limit);
}
