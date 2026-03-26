package com.loopers.application.handler;

import com.loopers.application.EventHandler;
import com.loopers.domain.ProductMetricsRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductViewedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductViewedEventHandler implements EventHandler<ProductViewedEventPayload> {
    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_VIEWED;
    }

    @Override
    public void handle(Event<ProductViewedEventPayload> event) {
        productMetricsRepository.incrementViewCount(event.getPayload().getProductId());
    }
}
