package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductDailyMetrics;
import com.loopers.domain.metrics.ProductDailyMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductDailyMetricsRepositoryImpl implements ProductDailyMetricsRepository {
    private final ProductDailyMetricsJpaRepository jpaRepository;

    @Override
    @Transactional
    public void upsertViewCount(Long productId, LocalDate date, ZonedDateTime updatedAt) {
        jpaRepository.upsertViewCount(productId, date, updatedAt);
    }

    @Override
    @Transactional
    public void upsertLikeCount(Long productId, LocalDate date, int delta, ZonedDateTime updatedAt) {
        jpaRepository.upsertLikeCount(productId, date, delta, updatedAt);
    }

    @Override
    @Transactional
    public void upsertOrderAmount(Long productId, LocalDate date, long amount, ZonedDateTime updatedAt) {
        jpaRepository.upsertOrderAmount(productId, date, amount, updatedAt);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductDailyMetrics> findByMetricDate(LocalDate date) {
        return jpaRepository.findByMetricDate(date);
    }
}
