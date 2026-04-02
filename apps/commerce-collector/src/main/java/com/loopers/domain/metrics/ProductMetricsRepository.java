package com.loopers.domain.metrics;

import java.util.Optional;

/**
 * ProductMetrics Repository 인터페이스.
 */
public interface ProductMetricsRepository {

    ProductMetrics save(ProductMetrics metrics);

    Optional<ProductMetrics> findByProductId(Long productId);

    ProductMetrics getOrCreate(Long productId);
}
