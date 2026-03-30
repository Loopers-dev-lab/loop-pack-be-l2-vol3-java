package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void upsertLike(Long productId, int delta) {
        jpaRepository.upsertLike(productId, delta);
    }

    @Override
    public void upsertOrder(Long productId, long quantity) {
        jpaRepository.upsertOrder(productId, quantity);
    }
}
