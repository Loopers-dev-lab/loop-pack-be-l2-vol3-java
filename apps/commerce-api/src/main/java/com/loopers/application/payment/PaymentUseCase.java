package com.loopers.application.payment;

import com.loopers.application.payment.command.CancelPaymentCommand;
import com.loopers.application.payment.command.CompletePaymentCommand;
import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.application.order.OrderApplicationService;
import com.loopers.application.order.query.OrderAccessRequest;
import com.loopers.domain.order.Order;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentUseCase {

    private final PaymentStartApplicationService paymentStartApplicationService;
    private final PaymentCancelApplicationService paymentCancelApplicationService;
    private final PaymentCompletionApplicationService paymentCompletionApplicationService;
    private final OrderApplicationService orderApplicationService;

    @Transactional
    public Payment start(String memberId, UUID orderId, CardType cardType, String cardNo, String callbackUrl) {
        Order order = orderApplicationService.getById(new OrderAccessRequest(orderId, memberId, false));

        StartPaymentCommand command = new StartPaymentCommand(
                memberId,
                orderId,
                cardType,
                cardNo,
                order.totalAmount(),
                callbackUrl
        );
        return paymentStartApplicationService.start(command);
    }

    @Transactional
    public Payment cancel(CancelPaymentCommand command) {
        return paymentCancelApplicationService.cancel(command);
    }

    @Transactional
    public Payment complete(CompletePaymentCommand command) {
        return paymentCompletionApplicationService.complete(command);
    }
}
