package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class ProductMetricsApp {

    private static final String TOPIC = "catalog-events";

    private final ProductMetricsService productMetricsService;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public boolean applyLikeDelta(String eventId, Long productDbId, int delta, LocalDateTime eventAt) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return false;
        }
        productMetricsService.applyLikeDelta(productDbId, delta, eventAt);
        eventHandledRepository.save(EventHandledModel.create(eventId, TOPIC));
        return true;
    }
}
