package com.loopers.application.process.checkout;

import com.loopers.application.process.checkout.event.OrderPaymentRequestEvent;
import com.loopers.application.process.checkout.event.OrderPointUsedEvent;
import com.loopers.domain.order.OrderCreateSagaProgressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPaymentRequestTriggerHandler {

    private final OrderCreateSagaProgressRepository progressRepository;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderPointUsedEvent event) {
        var progress = progressRepository.findByOrderId(event.orderId()).orElseThrow();
        if (progress.paymentAmount() <= 0) {
            progressRepository.save(progress.markCompleted());
            return;
        }
        progressRepository.save(progress.markPaymentRequested());
        applicationEventPublisher.publishEvent(new OrderPaymentRequestEvent(
                progress.memberId(),
                progress.orderId(),
                com.loopers.domain.payment.CardType.valueOf(progress.cardType()),
                progress.cardNo(),
                progress.paymentAmount()
        ));
    }
}
