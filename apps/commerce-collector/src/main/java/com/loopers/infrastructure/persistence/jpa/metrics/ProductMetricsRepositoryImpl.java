package com.loopers.infrastructure.persistence.jpa.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * ProductMetricsRepository 구현체.
 */
@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        ProductMetricsJpaEntity entity = jpaRepository.findByProductId(metrics.getProductId())
            .orElseGet(() -> new ProductMetricsJpaEntity(metrics.getProductId()));

        ProductMetricsMapper.updateEntity(entity, metrics);
        ProductMetricsJpaEntity saved = jpaRepository.save(entity);
        return ProductMetricsMapper.toDomain(saved);
    }

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId)
            .map(ProductMetricsMapper::toDomain);
    }

    @Override
    public ProductMetrics getOrCreate(Long productId) {
        return jpaRepository.findByProductId(productId)
            .map(ProductMetricsMapper::toDomain)
            .orElseGet(() -> {
                ProductMetricsJpaEntity entity = new ProductMetricsJpaEntity(productId);
                ProductMetricsJpaEntity saved = jpaRepository.save(entity);
                return ProductMetricsMapper.toDomain(saved);
            });
    }
}
