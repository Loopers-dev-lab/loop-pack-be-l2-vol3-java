package com.loopers.infrastructure.pg;

import java.math.BigDecimal;

public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    Long amount,
    String callbackUrl
) {
    public static PgPaymentRequest of(Long orderId, String cardType, String cardNo, BigDecimal amount, String callbackUrl) {
        return new PgPaymentRequest(String.format("%06d", orderId), cardType, cardNo, amount.longValue(), callbackUrl);
    }
}
