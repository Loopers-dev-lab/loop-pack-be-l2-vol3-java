package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Override
    public void incrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        productMetricsJpaRepository.incrementLikeCount(productId, occurredAt);
    }

    @Override
    public void decrementLikeCount(Long productId, ZonedDateTime occurredAt) {
        productMetricsJpaRepository.decrementLikeCount(productId, occurredAt);
    }

    @Override
    public void incrementSalesCount(Long productId, ZonedDateTime occurredAt) {
        productMetricsJpaRepository.incrementSalesCount(productId, occurredAt);
    }

    @Override
    public void incrementViewCount(Long productId, ZonedDateTime occurredAt) {
        productMetricsJpaRepository.incrementViewCount(productId, occurredAt);
    }
}