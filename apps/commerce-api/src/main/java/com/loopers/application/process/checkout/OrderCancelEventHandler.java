package com.loopers.application.process.checkout;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.outbox.OrderCancelRequestedOutboxMessage;
import com.loopers.application.outbox.OrderPaymentOutboxService;
import com.loopers.application.payment.PaymentQueryApplicationService;
import com.loopers.application.point.PointApplicationService;
import com.loopers.application.process.checkout.event.OrderCancelRequestedEvent;
import com.loopers.application.process.checkout.event.OrderPaymentCancelRequestEvent;
import com.loopers.application.product.ProductStockApplicationService;
import com.loopers.domain.order.Order;
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
public class OrderCancelEventHandler {

    private final OrderApplicationService orderApplicationService;
    private final CouponApplicationService couponApplicationService;
    private final PointApplicationService pointApplicationService;
    private final ProductStockApplicationService productStockApplicationService;
    private final PaymentQueryApplicationService paymentQueryApplicationService;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final OrderPaymentOutboxService orderPaymentOutboxService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(OrderCancelRequestedEvent event) {
        Order order = orderApplicationService.getByIdForSystem(event.orderId());
        if (!order.isCancelPending()) {
            return;
        }

        boolean stockWasDeducted = order.isStockDeducted();
        if (order.couponId() != null) {
            couponApplicationService.cancelUse(order.couponId(), order.memberId());
        }

        if (order.usedPointAmount() > 0) {
            pointApplicationService.restore(order.memberId(), order.usedPointAmount());
        }

        if (stockWasDeducted) {
            productStockApplicationService.restoreForOrder(order.items());
        }

        Order cancelled = orderApplicationService.confirmCancelForSystem(order.id());
        orderPaymentOutboxService.saveOrderCancelRequested(new OrderCancelRequestedOutboxMessage(
                UUID.randomUUID(),
                cancelled.id(),
                cancelled.memberId(),
                Instant.now()
        ));
        if (!stockWasDeducted && cancelled.isStockDeducted()) {
            productStockApplicationService.restoreForOrder(cancelled.items());
        }

        PaymentStatus paymentStatus = paymentQueryApplicationService
                .getPaymentByOrder(cancelled.memberId(), cancelled.id())
                .map(Payment::status)
                .orElse(null);

        if (paymentStatus == PaymentStatus.SUCCEEDED || paymentStatus == PaymentStatus.CANCEL_FAILED) {
            applicationEventPublisher.publishEvent(
                    new OrderPaymentCancelRequestEvent(cancelled.memberId(), cancelled.id())
            );
        }
    }
}
