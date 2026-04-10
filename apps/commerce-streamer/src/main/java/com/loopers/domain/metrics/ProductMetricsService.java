package com.loopers.domain.metrics;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductMetricsService {

    private final ProductMetricsRepository productMetricsRepository;

    @Transactional
    public ProductMetrics findOrCreateByProductId(Long productId) {
        return productMetricsRepository.findByProductId(productId)
                .orElseGet(() -> productMetricsRepository.save(ProductMetrics.create(productId)));
    }

    @Transactional
    public void increaseLikes(Long productId) {
        ProductMetrics metrics = findOrCreateByProductId(productId);
        metrics.increaseLikes();
    }

    @Transactional
    public void decreaseLikes(Long productId) {
        ProductMetrics metrics = findOrCreateByProductId(productId);
        metrics.decreaseLikes();
    }

    @Transactional
    public void increaseViews(Long productId) {
        ProductMetrics metrics = findOrCreateByProductId(productId);
        metrics.increaseViews();
    }

    @Transactional
    public void increaseOrders(Long productId, int count) {
        ProductMetrics metrics = findOrCreateByProductId(productId);
        metrics.increaseOrders(count);
    }
}
