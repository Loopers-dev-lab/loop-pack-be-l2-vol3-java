package com.loopers.application.handler;

import com.loopers.application.EventHandler;
import com.loopers.infrastructure.ProductMetricsDailyRepository;
import com.loopers.infrastructure.ProductMetricsRepository;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.OrderedProduct;
import com.loopers.event.payload.PaymentCompletedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PaymentCompletedEventHandler implements EventHandler<PaymentCompletedEventPayload> {
    private final ProductMetricsRepository productMetricsRepository;
    private final ProductMetricsDailyRepository productMetricsDailyRepository;
    private final Clock clock;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PAYMENT_COMPLETED;
    }

    @Override
    public void handle(Event<PaymentCompletedEventPayload> event) {
        PaymentCompletedEventPayload payload = event.getPayload();
        LocalDate today = LocalDate.now(clock);

        for (Long productId : payload.getProductIds()) {
            productMetricsRepository.incrementOrderLineCount(productId);
        }

        List<OrderedProduct> orderedProducts = payload.getOrderedProducts();
        if (orderedProducts == null || orderedProducts.isEmpty()) {
            return;
        }

        for (OrderedProduct product : orderedProducts) {
            long orderAmount = product.getPrice() * product.getQuantity();
            productMetricsDailyRepository.incrementOrderLineCountAndAmount(
                    product.getProductId(), today, orderAmount
            );
        }
    }
}
