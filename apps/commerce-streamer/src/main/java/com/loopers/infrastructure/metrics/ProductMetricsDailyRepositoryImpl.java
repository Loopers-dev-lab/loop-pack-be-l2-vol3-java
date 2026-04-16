package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;

@Repository
@RequiredArgsConstructor
public class ProductMetricsDailyRepositoryImpl implements ProductMetricsDailyRepository {

    private final ProductMetricsDailyJpaRepository jpaRepository;

    @Override
    public void incrementViewCountBy(Long productId, LocalDate metricDate, int count) {
        jpaRepository.incrementViewCountBy(productId, metricDate, count);
    }

    @Override
    public void incrementLikeCount(Long productId, LocalDate metricDate) {
        jpaRepository.incrementLikeCount(productId, metricDate);
    }

    @Override
    public void decrementLikeCount(Long productId, LocalDate metricDate) {
        jpaRepository.decrementLikeCount(productId, metricDate);
    }

    @Override
    public void incrementOrderCount(Long productId, LocalDate metricDate, long amount) {
        jpaRepository.incrementOrderCount(productId, metricDate, amount);
    }
}
