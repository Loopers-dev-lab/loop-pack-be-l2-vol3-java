package com.loopers.domain.product;

import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);
    void incrementLikeCount(Long refProductId);
    void decrementLikeCount(Long refProductId);
}
