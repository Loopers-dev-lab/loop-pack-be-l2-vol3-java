package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PgCallbackCommand;

public class PaymentV1Dto {

    public record CallbackRequest(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
        public PgCallbackCommand toCommand() {
            return new PgCallbackCommand(transactionKey, status, reason);
        }
    }
}
