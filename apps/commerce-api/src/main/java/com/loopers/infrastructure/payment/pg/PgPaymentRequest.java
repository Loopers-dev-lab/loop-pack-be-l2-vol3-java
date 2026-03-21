package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.CardType;

public record PgPaymentRequest(
    String orderId,
    CardType cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {
}
