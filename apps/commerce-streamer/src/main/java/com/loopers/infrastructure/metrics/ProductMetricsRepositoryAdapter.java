package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class ProductMetricsRepositoryAdapter implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void upsertViewCount(Long productId, LocalDate metricDate, int delta) {
        jpaRepository.upsertViewCount(productId, metricDate, delta);
    }

    @Override
    public void upsertLikeCount(Long productId, LocalDate metricDate, int delta) {
        jpaRepository.upsertLikeCount(productId, metricDate, delta);
    }

    @Override
    public void upsertSalesCount(Long productId, LocalDate metricDate, int delta) {
        jpaRepository.upsertSalesCount(productId, metricDate, delta);
    }

    @Override
    public void upsertPrice(Long productId, LocalDate metricDate, BigDecimal price, Instant occurredAt) {
        jpaRepository.upsertPrice(productId, metricDate, price, occurredAt);
    }
}
