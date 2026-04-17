package com.loopers.infrastructure.metrics.repository;

import com.loopers.domain.metrics.repository.ProductMetricsDailyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@RequiredArgsConstructor
@Component
public class ProductMetricsDailyRepositoryImpl implements ProductMetricsDailyRepository {

    private final ProductMetricsDailyJpaRepository productMetricsDailyJpaRepository;

    @Override
    public void incrementViewCount(LocalDate metricDate, Long productId, double scoreDelta) {
        productMetricsDailyJpaRepository.incrementViewCount(metricDate, productId, scoreDelta);
    }

    @Override
    public void incrementLikeCount(LocalDate metricDate, Long productId, double scoreDelta) {
        productMetricsDailyJpaRepository.incrementLikeCount(metricDate, productId, scoreDelta);
    }

    @Override
    public void decrementLikeCount(LocalDate metricDate, Long productId, double scoreDelta) {
        productMetricsDailyJpaRepository.decrementLikeCount(metricDate, productId, scoreDelta);
    }

    @Override
    public void incrementOrderCount(LocalDate metricDate, Long productId, double scoreDelta) {
        productMetricsDailyJpaRepository.incrementOrderCount(metricDate, productId, scoreDelta);
    }
}
