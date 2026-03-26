package com.loopers.application.collector;

import com.loopers.domain.collector.ProductMetricsModel;
import com.loopers.infrastructure.collector.ProductMetricsJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

@Service
@RequiredArgsConstructor
public class ProductMetricsAggregationService {

    private final ProductMetricsJpaRepository productMetricsJpaRepository;

    @Transactional
    public void applyLikeDelta(Long productId, long delta, ZonedDateTime occurredAt) {
        ProductMetricsModel metrics = productMetricsJpaRepository.findByProductIdForUpdate(productId)
            .orElseGet(() -> productMetricsJpaRepository.save(new ProductMetricsModel(productId)));
        metrics.applyLikeDelta(delta, occurredAt);
    }

    @Transactional
    public void applyView(Long productId, ZonedDateTime occurredAt) {
        ProductMetricsModel metrics = productMetricsJpaRepository.findByProductIdForUpdate(productId)
            .orElseGet(() -> productMetricsJpaRepository.save(new ProductMetricsModel(productId)));
        metrics.applyView(occurredAt);
    }

    @Transactional
    public void applySales(Long productId, long quantity, ZonedDateTime occurredAt) {
        ProductMetricsModel metrics = productMetricsJpaRepository.findByProductIdForUpdate(productId)
            .orElseGet(() -> productMetricsJpaRepository.save(new ProductMetricsModel(productId)));
        metrics.applySales(quantity, occurredAt);
    }
}
