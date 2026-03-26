package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetricsModel> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId);
    }

    @Override
    public ProductMetricsModel save(ProductMetricsModel model) {
        return jpaRepository.save(model);
    }
}
