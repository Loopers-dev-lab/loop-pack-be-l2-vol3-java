package com.loopers.application.payment;

import com.loopers.domain.payment.CardType;

public record PgPaymentRequest(
    Long orderId,
    CardType cardType,
    String cardNo,
    Long amount
) {}
