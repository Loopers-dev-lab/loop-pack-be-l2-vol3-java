package com.loopers.domain.metrics;

import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetrics> findByProductId(Long productId);

    void incrementLikeCount(Long productId);

    void decrementLikeCount(Long productId);

    void incrementOrderCount(Long productId);

    void incrementViewCount(Long productId);
}
