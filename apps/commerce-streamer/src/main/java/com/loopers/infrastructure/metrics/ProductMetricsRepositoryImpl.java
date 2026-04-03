package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@RequiredArgsConstructor
@Repository
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public Optional<ProductMetrics> findByProductId(Long productId) {
        return productMetricsJpaRepository.findByProductId(productId);
    }

    @Override
    public void incrementLikeCount(Long productId) {
        productMetricsJpaRepository.incrementLikeCount(productId);
    }

    @Override
    public void decrementLikeCount(Long productId) {
        productMetricsJpaRepository.decrementLikeCount(productId);
    }

    @Override
    public void incrementOrderCount(Long productId) {
        productMetricsJpaRepository.incrementOrderCount(productId);
    }

    @Override
    public void incrementViewCount(Long productId) {
        productMetricsJpaRepository.incrementViewCount(productId);
    }
}
