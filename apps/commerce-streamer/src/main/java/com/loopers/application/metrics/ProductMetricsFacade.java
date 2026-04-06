package com.loopers.application.metrics;

import com.loopers.domain.metrics.ProductMetricsRepository;
import com.loopers.infrastructure.eventhandled.EventHandled;
import com.loopers.infrastructure.eventhandled.EventHandledJpaRepository;
import com.loopers.interfaces.consumer.payload.CatalogEventPayload;
import com.loopers.interfaces.consumer.payload.OrderCreatedEventPayload;
import com.loopers.interfaces.consumer.payload.ProductViewEventPayload;
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
        productMetricsRepository.upsertLike(payload.productId(), payload.delta());
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }

    @Transactional
    public void applyOrder(OrderCreatedEventPayload payload) {
        if (eventHandledJpaRepository.existsById(payload.eventId())) {
            return;
        }
        payload.items().forEach(item ->
                productMetricsRepository.upsertOrder(item.productId(), item.quantity()));
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }

    @Transactional
    public void applyView(ProductViewEventPayload payload) {
        productMetricsRepository.upsertView(payload.productId());
    }
}
