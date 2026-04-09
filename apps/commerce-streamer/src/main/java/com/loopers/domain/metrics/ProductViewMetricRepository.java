package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.util.List;

public interface ProductViewMetricRepository {

    void batchUpsert(List<ProductViewMetric> metrics);

    void upsert(Long productId, LocalDateTime bucketTime, long viewCount);
}
