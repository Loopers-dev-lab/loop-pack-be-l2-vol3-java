package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.NewPayment;
import com.loopers.domain.shared.Money;

public record CreatePaymentCommand(
        Long userId,
        String orderKey,
        String cardType,
        String cardNo,
        String callbackUrl
) {

    public NewPayment toNewPayment(Long orderId, String transactionKey, Money amount) {
        return new NewPayment(
                userId,
                orderId,
                transactionKey,
                CardType.valueOf(cardType),
                cardNo,
                amount
        );
    }
}
