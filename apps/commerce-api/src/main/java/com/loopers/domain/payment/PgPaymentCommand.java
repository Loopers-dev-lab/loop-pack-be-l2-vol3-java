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
    @Override
    public String toString() {
        String masked = (cardNo != null && cardNo.length() > 4)
                ? "*".repeat(cardNo.length() - 4) + cardNo.substring(cardNo.length() - 4)
                : (cardNo != null ? "*".repeat(cardNo.length()) : "null");
        return "PgPaymentCommand[orderId=" + orderId +
                ", userId=" + userId +
                ", cardType=" + cardType +
                ", cardNo=" + masked +
                ", amount=" + amount +
                ", callbackUrl=" + callbackUrl + "]";
    }
}
