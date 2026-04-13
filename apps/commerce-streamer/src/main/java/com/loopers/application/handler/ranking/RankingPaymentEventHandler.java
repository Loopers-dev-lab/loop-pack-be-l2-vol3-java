package com.loopers.application.handler.ranking;

import com.loopers.application.EventHandler;
import com.loopers.domain.ranking.RankingScoreUpdater;
import com.loopers.event.Event;
import com.loopers.event.EventPayload;
import com.loopers.event.EventType;
import com.loopers.event.payload.OrderedProduct;
import com.loopers.event.payload.PaymentCompletedEventPayload;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class RankingPaymentEventHandler implements EventHandler<PaymentCompletedEventPayload> {
    private final RankingScoreUpdater rankingScoreUpdater;

    @Override
    public boolean supports(Event<EventPayload> event) {
        return event.getType() == EventType.PAYMENT_COMPLETED;
    }

    @Override
    public void handle(Event<PaymentCompletedEventPayload> event) {
        List<OrderedProduct> orderedProducts = event.getPayload().getOrderedProducts();
        if (orderedProducts == null || orderedProducts.isEmpty()) {
            return;
        }

        for (OrderedProduct product : orderedProducts) {
            rankingScoreUpdater.incrementOrder(
                    product.getProductId(),
                    product.getPrice(),
                    product.getQuantity()
            );
        }
    }
}
