package com.loopers.domain.payment;

import java.math.BigDecimal;

public record PgPaymentCommand(
        Long orderId,
        Long userId,
        String cardType,
        String cardNo,
        BigDecimal amount,
        String callbackUrl
) {
}
