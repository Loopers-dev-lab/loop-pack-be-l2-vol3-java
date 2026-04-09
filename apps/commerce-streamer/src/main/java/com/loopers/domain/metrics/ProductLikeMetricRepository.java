package com.loopers.domain.metrics;

import java.time.LocalDateTime;

public interface ProductLikeMetricRepository {

    void upsert(Long productId, LocalDateTime bucketTime, int delta);
}
