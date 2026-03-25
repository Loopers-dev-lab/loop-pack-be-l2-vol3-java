package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetrics;
import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.eventhandled.EventHandled;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class ProductMetricsFacade {

    private final ProductMetricsRepository productMetricsRepository;
    private final EventHandledJpaRepository eventHandledJpaRepository;

    @Transactional
    public void applyLike(CatalogEventPayload payload) {
        if (eventHandledJpaRepository.existsById(payload.eventId())) {
            return;
        }
        ProductMetrics metrics = productMetricsRepository.findByProductId(payload.productId())
                .orElse(ProductMetrics.of(payload.productId()));
        metrics.applyLike(payload.delta());
        productMetricsRepository.save(metrics);
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }

    @Transactional
    public void applyOrder(OrderCreatedEventPayload payload) {
        if (eventHandledJpaRepository.existsById(payload.eventId())) {
            return;
        }
        payload.items().forEach(item -> {
            ProductMetrics metrics = productMetricsRepository.findByProductId(item.productId())
                    .orElse(ProductMetrics.of(item.productId()));
            metrics.applyOrder(item.quantity());
            productMetricsRepository.save(metrics);
        });
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }
}
