package com.loopers.infrastructure.product;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {
}
