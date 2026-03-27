package com.loopers.domain.metrics;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);
    ProductMetricsModel save(ProductMetricsModel model);
    void upsertLikeDelta(Long refProductId, int delta, LocalDateTime eventAt);
}
