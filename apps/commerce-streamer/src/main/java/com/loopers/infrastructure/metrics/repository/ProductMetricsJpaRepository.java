package com.loopers.infrastructure.metrics.repository;

import com.loopers.infrastructure.metrics.entity.ProductMetricsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {
}
