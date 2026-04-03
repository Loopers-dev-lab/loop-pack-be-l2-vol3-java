package com.loopers.application.process.checkout;

import com.loopers.application.process.checkout.event.OrderCreateRequestedEvent;
import com.loopers.application.process.checkout.event.OrderCouponAppliedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderCreateRequestedHandler {

    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCreateRequestedEvent event) {
        applicationEventPublisher.publishEvent(new OrderCouponAppliedEvent(event.orderId()));
    }
}
