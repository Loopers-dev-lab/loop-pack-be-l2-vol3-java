package com.loopers.application.payment;

import com.loopers.application.payment.command.StartPaymentCommand;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.infrastructure.payment.PaymentRecoveryRequiredException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentStartApplicationService {

    private final PaymentStartPreparationApplicationService paymentStartPreparationApplicationService;
    private final PaymentStartCompletionApplicationService paymentStartCompletionApplicationService;
    private final PaymentGateway paymentGateway;

    public Payment start(StartPaymentCommand command) {
        PaymentStartPreparationResult preparationResult = paymentStartPreparationApplicationService.prepare(command);
        if (!preparationResult.requiresGatewayRequest()) {
            return preparationResult.payment();
        }

        Payment requested = preparationResult.payment();

        PaymentGateway.PaymentGatewayTransaction gatewayResult;
        try {
            gatewayResult = paymentGateway.requestPayment(
                    new PaymentGateway.PaymentGatewayRequest(
                            command.memberId(),
                            command.orderId().toString(),
                            command.cardType(),
                            command.cardNo(),
                            command.amount(),
                            command.callbackUrl()
                    )
            );
        } catch (PaymentRecoveryRequiredException e) {
            return requested;
        }

        return paymentStartCompletionApplicationService.complete(requested, gatewayResult);
    }
}
