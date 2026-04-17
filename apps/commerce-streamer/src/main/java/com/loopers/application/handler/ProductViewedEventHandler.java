package com.loopers.application.handler;

import com.loopers.application.EventHandler;
import com.loopers.infrastructure.ProductMetricsDailyRepository;
import com.loopers.infrastructure.ProductMetricsRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductViewedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

@Component
@RequiredArgsConstructor
public class ProductViewedEventHandler implements EventHandler<ProductViewedEventPayload> {
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final Clock clock;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_VIEWED;
    }

    @Override
    public void handle(Event<ProductViewedEventPayload> event) {
        Long productId = event.getPayload().getProductId();
        productMetricsRepository.incrementViewCount(productId);
        productMetricsDailyRepository.incrementViewCount(productId, LocalDate.now(clock));
    }
}
