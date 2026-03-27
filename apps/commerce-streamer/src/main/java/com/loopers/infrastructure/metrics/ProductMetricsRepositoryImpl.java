package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void incrementLikeCount(Long productId, long delta) {
        jpaRepository.upsertLikeCount(productId, delta);
    }

    @Override
    public void incrementViewCount(Long productId, long delta) {
        jpaRepository.upsertViewCount(productId, delta);
    }

    @Override
    public void incrementSales(Long productId, long countDelta, BigDecimal amountDelta) {
        jpaRepository.upsertSales(productId, countDelta, amountDelta);
    }
}
