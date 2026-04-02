package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetrics> findByProductId(Long productId);

    ProductMetrics save(ProductMetrics metrics);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);

    void incrementViewCount(Long productId);

    void incrementSalesCount(Long productId);
}
