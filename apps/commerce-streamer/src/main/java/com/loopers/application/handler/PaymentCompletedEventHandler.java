package com.loopers.application.handler;

import com.loopers.application.EventHandler;
import com.loopers.domain.ProductMetricsRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.PaymentCompletedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentCompletedEventHandler implements EventHandler<PaymentCompletedEventPayload> {
    private final ProductMetricsRepository productMetricsRepository;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PAYMENT_COMPLETED;
    }

    @Override
    public void handle(Event<PaymentCompletedEventPayload> event) {
        for (Long productId : event.getPayload().getProductIds()) {
            productMetricsRepository.incrementOrderCount(productId);
        }
    }
}
