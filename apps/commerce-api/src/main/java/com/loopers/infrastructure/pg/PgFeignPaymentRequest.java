package com.loopers.infrastructure.pg;

import java.math.BigDecimal;

public record PgFeignPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        String callbackUrl
) {
    @Override
    public String toString() {
        String masked = (cardNo != null && cardNo.length() > 4)
                ? "*".repeat(cardNo.length() - 4) + cardNo.substring(cardNo.length() - 4)
                : (cardNo != null ? "*".repeat(cardNo.length()) : "null");
        return "PgFeignPaymentRequest[orderId=" + orderId +
                ", cardType=" + cardType +
                ", cardNo=" + masked +
                ", amount=" + amount +
                ", callbackUrl=" + callbackUrl + "]";
    }
}
