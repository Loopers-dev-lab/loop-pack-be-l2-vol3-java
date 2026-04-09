package com.loopers.application.order;

import com.loopers.application.process.checkout.event.OrderPaymentCancelRequestEvent;
import com.loopers.application.process.checkout.event.OrderPaymentRequestEvent;
import com.loopers.application.payment.event.PaymentStatusChangedEvent;
import com.loopers.application.payment.PaymentCancelApplicationService;
import com.loopers.application.payment.PaymentStartApplicationService;
import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class OrderPaymentEventHandler {

    private final PaymentStartApplicationService paymentStartApplicationService;
    private final PaymentCancelApplicationService paymentCancelApplicationService;
    private final OrderApplicationService orderApplicationService;

    @Value("${loopers.payment.callback-url:http://localhost:8080/api/v1/payments/callback}")
    private String callbackUrl;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaymentRequest(OrderPaymentRequestEvent event) {
        if (event.amount() <= 0) {
            return;
        }

        Order order = orderApplicationService.getByIdForSystem(event.orderId());
        if (order.isCancelled()) {
            return;
        }

        paymentStartApplicationService.start(
                new StartPaymentCommand(
                        event.memberId(),
                        event.orderId(),
                        event.cardType(),
                        event.cardNo(),
                        event.amount(),
                        callbackUrl
                )
        );
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderPaymentCancelRequest(OrderPaymentCancelRequestEvent event) {
        try {
            paymentCancelApplicationService.cancel(new CancelPaymentCommand(event.memberId(), event.orderId()));
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND) {
                return;
            }
            throw e;
        }
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handlePaymentStatusChanged(PaymentStatusChangedEvent event) {
        if (event.afterStatus() != PaymentStatus.SUCCEEDED) {
            return;
        }

        Order order = orderApplicationService.getByIdForSystem(event.orderId());
        if (!order.isCancelled()) {
            return;
        }

        try {
            paymentCancelApplicationService.cancel(new CancelPaymentCommand(event.memberId(), event.orderId()));
        } catch (CoreException e) {
            if (e.getErrorType() == ErrorType.NOT_FOUND) {
                return;
            }
            throw e;
        }
    }
}
