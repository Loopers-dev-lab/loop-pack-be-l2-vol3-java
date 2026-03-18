package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.PaymentMethod;

public record CreatePaymentCommand(
        Long userId,
        String orderKey,
        String cardType,
        String cardNo,
        String callbackUrl
) {

    public PaymentMethod toPaymentMethod() {
        return new PaymentMethod(CardType.valueOf(cardType), cardNo);
    }
}
