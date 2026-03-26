package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {
    Optional<ProductMetricsModel> findByProductId(Long productId);
}
