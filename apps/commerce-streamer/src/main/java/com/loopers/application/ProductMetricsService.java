package com.loopers.application;

import com.loopers.domain.metrics.ProductMetricsModel;
import com.loopers.domain.metrics.ProductMetricsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@RequiredArgsConstructor
@Component
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public void incrementViewCount(Long productId) {
        ProductMetricsModel metrics = getOrCreate(productId);
        metrics.incrementViewCount();
    }

    @Transactional
    public void incrementLikeCount(Long productId) {
        ProductMetricsModel metrics = getOrCreate(productId);
        metrics.incrementLikeCount();
    }

    @Transactional
    public void decrementLikeCount(Long productId) {
        ProductMetricsModel metrics = getOrCreate(productId);
        metrics.decrementLikeCount();
    }

    @Transactional
    public void addSales(Long productId, int quantity, long amount) {
        ProductMetricsModel metrics = getOrCreate(productId);
        metrics.addSales(quantity, amount);
    }

    @Transactional(readOnly = true)
    public boolean isStaleEvent(Long productId, LocalDateTime eventCreatedAt) {
        return productMetricsRepository.findByProductId(productId)
                .map(metrics -> metrics.getUpdatedAt().isAfter(eventCreatedAt))
                .orElse(false);
    }

    private ProductMetricsModel getOrCreate(Long productId) {
        return productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(new ProductMetricsModel(productId)));
    }
}
