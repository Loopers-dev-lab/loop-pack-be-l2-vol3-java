package com.loopers.domain.product;

import java.util.Optional;

public interface ProductMetricsRepository {
    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);
}
