package com.loopers.infrastructure.metrics.persistence;

import org.springframework.stereotype.Repository;

import com.loopers.domain.metrics.ProductMetricsRepository;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void upsertLikeCount(Long productId, Long delta) {
        productMetricsJpaRepository.upsertLikeCount(productId, delta);
    }

    @Override
    public void upsertOrderCount(Long productId, Long quantity) {
        productMetricsJpaRepository.upsertOrderCount(productId, quantity);
    }

    @Override
    public void upsertViewCount(Long productId) {
        productMetricsJpaRepository.upsertViewCount(productId);
    }
}
