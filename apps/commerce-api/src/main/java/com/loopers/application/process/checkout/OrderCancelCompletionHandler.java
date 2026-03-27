package com.loopers.application.process.checkout;

import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.outbox.OrderPaymentOutboxService;
import com.loopers.application.payment.PaymentQueryApplicationService;
import com.loopers.application.process.checkout.event.OrderCancelCompensationStepCompletedEvent;
import com.loopers.application.process.checkout.event.OrderCancelledConfirmedEvent;
import com.loopers.application.process.checkout.event.OrderPaymentCancelRequestEvent;
import com.loopers.contract.kafka.OrderCancelRequestedOutboxMessage;
import com.loopers.domain.order.OrderCancelSagaProgressRepository;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderCancelCompletionHandler {

    private final OrderCancelSagaProgressRepository progressRepository;
    private final OrderApplicationService orderApplicationService;
    private final PaymentQueryApplicationService paymentQueryApplicationService;
    private final OrderPaymentOutboxService orderPaymentOutboxService;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCancelCompensationStepCompletedEvent event) {
        var progress = progressRepository.findByOrderId(event.orderId()).orElseThrow();
        progress = switch (event.stepType()) {
            case COUPON -> progress.markCouponDone();
            case POINT -> progress.markPointDone();
            case STOCK -> progress.markStockDone();
        };
        progressRepository.save(progress);
        if (!progress.isCompleted()) {
            return;
        }

        var cancelled = orderApplicationService.confirmCancelForSystem(event.orderId());
        orderPaymentOutboxService.saveOrderCancelRequested(new OrderCancelRequestedOutboxMessage(
                UUID.randomUUID(),
                cancelled.id(),
                cancelled.memberId(),
                Instant.now()
        ));
        applicationEventPublisher.publishEvent(new OrderCancelledConfirmedEvent(cancelled.id(), cancelled.memberId()));

        PaymentStatus paymentStatus = paymentQueryApplicationService
                .getPaymentByOrder(cancelled.memberId(), cancelled.id())
                .map(Payment::status)
                .orElse(null);
        if (paymentStatus == PaymentStatus.SUCCEEDED || paymentStatus == PaymentStatus.CANCEL_FAILED) {
            applicationEventPublisher.publishEvent(new OrderPaymentCancelRequestEvent(cancelled.memberId(), cancelled.id()));
        }
    }
}
