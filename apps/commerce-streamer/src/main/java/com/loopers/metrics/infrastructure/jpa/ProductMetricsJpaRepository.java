package com.loopers.metrics.infrastructure.jpa;


import com.loopers.metrics.infrastructure.entity.ProductMetricsEntity;
import com.loopers.metrics.infrastructure.entity.ProductMetricsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;


/**
 * 상품 메트릭 JPA 레포지토리 — 복합 PK (metric_date, product_id)
 */
public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsEntity, ProductMetricsId> {

	Optional<ProductMetricsEntity> findByMetricDateAndProductId(LocalDate metricDate, Long productId);

}
