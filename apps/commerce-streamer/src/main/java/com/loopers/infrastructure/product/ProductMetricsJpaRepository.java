package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductMetrics;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetrics, Long> {
    Optional<ProductMetrics> findByProductIdAndDate(Long productId, LocalDate date);
}
