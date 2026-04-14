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

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

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
        LocalDateTime metricHour = toMetricHour(payload.occurredAt());
        productMetricsRepository.upsertLike(payload.productId(), payload.delta(), metricHour);
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }

    @Transactional
    public void applyOrder(OrderCreatedEventPayload payload) {
        if (eventHandledJpaRepository.existsById(payload.eventId())) {
            return;
        }
        LocalDateTime metricHour = toMetricHour(payload.occurredAt());
        payload.items().forEach(item -> {
            long salesAmount = (long) item.quantity() * item.unitPrice();
            productMetricsRepository.upsertOrder(item.productId(), item.quantity(), salesAmount, metricHour);
        });
        eventHandledJpaRepository.save(EventHandled.of(payload.eventId()));
    }

    @Transactional
    public void applyView(ProductViewEventPayload payload) {
        LocalDateTime metricHour = toMetricHour(payload.occurredAt());
        productMetricsRepository.upsertView(payload.productId(), metricHour);
    }

    private LocalDateTime toMetricHour(ZonedDateTime occurredAt) {
        return occurredAt.withZoneSameInstant(ZoneOffset.UTC)
                .truncatedTo(ChronoUnit.HOURS)
                .toLocalDateTime();
    }
}
