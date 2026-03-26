package com.loopers.application.handler;

import com.loopers.application.EventHandler;
import com.loopers.domain.ProductMetricsRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.ProductUnlikedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ProductUnlikedEventHandler implements EventHandler<ProductUnlikedEventPayload> {
    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PRODUCT_UNLIKED;
    }

    @Override
    public void handle(Event<ProductUnlikedEventPayload> event) {
        productMetricsRepository.decrementLikeCount(event.getPayload().getProductId());
    }
}
