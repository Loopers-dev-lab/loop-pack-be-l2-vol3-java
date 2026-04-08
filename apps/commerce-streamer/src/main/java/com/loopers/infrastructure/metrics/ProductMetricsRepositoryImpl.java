package com.loopers.infrastructure.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class ProductMetricsRepositoryImpl implements ProductMetricsRepository {

    private final ProductMetricsJpaRepository jpaRepository;

    @Override
    public void upsertLike(Long productId, int delta, LocalDateTime metricHour) {
        jpaRepository.upsertLike(productId, delta, metricHour);
    }

    @Override
    public void upsertOrder(Long productId, long quantity, long salesAmount, LocalDateTime metricHour) {
        jpaRepository.upsertOrder(productId, quantity, salesAmount, metricHour);
    }

    @Override
    public void upsertView(Long productId, LocalDateTime metricHour) {
        jpaRepository.upsertView(productId, metricHour);
    }
}
