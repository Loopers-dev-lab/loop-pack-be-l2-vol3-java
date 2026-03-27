package com.loopers.infrastructure.product;

import com.loopers.domain.product.ProductMetricsModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductMetricsJpaRepository extends JpaRepository<ProductMetricsModel, Long> {

    Optional<ProductMetricsModel> findByRefProductId(Long refProductId);
}
