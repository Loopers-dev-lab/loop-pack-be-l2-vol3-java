package com.loopers.domain.metrics.repository;

import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductMetricsRepository {

    Optional<ProductMetricsEntity> findById(Long productId);

    ProductMetricsEntity save(ProductMetricsEntity metrics);

    List<ProductMetricsEntity> findByUpdatedAtAfter(LocalDateTime since);
}
