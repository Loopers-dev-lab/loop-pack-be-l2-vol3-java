package com.loopers.application.process.checkout;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.process.checkout.event.OrderCancelCompensationRequestedEvent;
import com.loopers.application.process.checkout.event.OrderCancelRequestedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderCancelRequestedHandler {

    private final OrderApplicationService orderApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCancelRequestedEvent event) {
        if (!orderApplicationService.getByIdForSystem(event.orderId()).isCancelPending()) {
            return;
        }
        applicationEventPublisher.publishEvent(new OrderCancelCompensationRequestedEvent(event.orderId()));
    }
}
