package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDaily;
import com.loopers.domain.metrics.ProductMetricsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDaily, ProductMetricsDailyId> {
}
