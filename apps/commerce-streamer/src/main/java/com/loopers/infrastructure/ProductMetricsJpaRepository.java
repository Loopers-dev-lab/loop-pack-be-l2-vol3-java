package com.loopers.infrastructure;

import com.loopers.domain.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {}
