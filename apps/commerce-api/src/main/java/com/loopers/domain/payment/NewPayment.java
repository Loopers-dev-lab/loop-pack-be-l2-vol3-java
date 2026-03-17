package com.loopers.domain.payment;

import com.loopers.domain.shared.Money;

public record NewPayment(
        Long userId,
        Long orderId,
        String transactionKey,
        CardType cardType,
        String cardNo,
        Money amount
) {
}
