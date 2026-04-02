package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return jpaRepository.findByProductId(productId)
            .map(ProductMetricsEntity::toDomain);
    }

    @Override
    public ProductMetrics save(ProductMetrics metrics) {
        return ProductMetricsEntity.toDomain(jpaRepository.save(new ProductMetricsEntity(metrics)));
    }

    @Override
    public void incrementLikeCount(Long productId) {
        jpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        jpaRepository.decrementLikeCount(productId);
    }

    @Override
    public void incrementViewCount(Long productId) {
        jpaRepository.incrementViewCount(productId);
    }

    @Override
    public void incrementSalesCount(Long productId) {
        jpaRepository.incrementSalesCount(productId);
    }
}
