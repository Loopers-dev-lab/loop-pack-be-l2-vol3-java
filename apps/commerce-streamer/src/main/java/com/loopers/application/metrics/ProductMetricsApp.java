package com.loopers.application.metrics;

import com.loopers.domain.eventhandled.EventHandledModel;
import com.loopers.domain.eventhandled.EventHandledRepository;
import com.loopers.domain.metrics.ProductMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ProductMetricsApp {

    private final ProductMetricsService productMetricsService;
    private final EventHandledRepository eventHandledRepository;

    @Transactional
    public void applyLikeDelta(String eventId, Long productDbId, int delta, String topic) {
        if (eventHandledRepository.existsByEventId(eventId)) {
            return;
        }
        productMetricsService.adjustLikeCount(productDbId, delta);
        eventHandledRepository.save(EventHandledModel.create(eventId, topic));
    }
}
