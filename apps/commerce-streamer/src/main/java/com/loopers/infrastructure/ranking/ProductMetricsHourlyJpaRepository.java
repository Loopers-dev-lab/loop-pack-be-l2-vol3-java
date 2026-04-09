package com.loopers.infrastructure.ranking;

import com.loopers.domain.ranking.ProductMetricsHourly;
import com.loopers.domain.ranking.ProductMetricsHourlyId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductMetricsHourlyJpaRepository
        extends JpaRepository<ProductMetricsHourly, ProductMetricsHourlyId> {
}
