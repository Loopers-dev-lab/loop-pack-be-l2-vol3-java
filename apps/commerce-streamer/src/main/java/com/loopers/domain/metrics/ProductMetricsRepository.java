package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);
    ProductMetricsModel save(ProductMetricsModel model);
}
