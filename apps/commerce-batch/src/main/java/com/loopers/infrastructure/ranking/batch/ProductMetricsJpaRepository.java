package com.loopers.infrastructure.ranking.batch;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, Long> {
}
