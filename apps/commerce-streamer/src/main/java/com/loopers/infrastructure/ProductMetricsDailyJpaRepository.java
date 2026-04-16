package com.loopers.infrastructure;

import com.loopers.domain.ProductMetricsDaily;
import com.loopers.domain.ProductMetricsDailyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsDailyJpaRepository extends JpaRepository<ProductMetricsDaily, ProductMetricsDailyId> {
}
