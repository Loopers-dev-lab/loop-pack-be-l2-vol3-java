package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {
    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public ProductMetrics save(ProductMetrics productMetrics) {
        return productMetricsJpaRepository.save(productMetrics);
    }

    @Override
    public Optional<ProductMetrics> findById(Long productId) {
        return productMetricsJpaRepository.findById(productId);
    }

    @Override
    public void insertIgnore(Long productId) {
        productMetricsJpaRepository.insertIgnore(productId);
    }

    @Override
    public int incrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementLikeCount(productId, occurredAt);
    }

    @Override
    public int decrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.decrementLikeCount(productId, occurredAt);
    }

    @Override
    public int incrementViewCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementViewCount(productId, occurredAt);
    }

    @Override
    public int incrementSalesCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.incrementSalesCount(productId, occurredAt);
    }

    @Override
    public int decrementSalesCount(Long productId, ZonedDateTime occurredAt) {
        return productMetricsJpaRepository.decrementSalesCount(productId, occurredAt);
    }
}
