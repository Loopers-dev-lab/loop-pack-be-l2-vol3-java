package com.loopers.infrastructure.payment;

/**
 * PG-Simulator 결제 요청 Body (06 §2.1).
 * POST /api/v1/payments
 */
public record PgSimulatorRequest(
        Long orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
    @Override
    public String toString() {
        String maskedCardNo;
        if (cardNo == null || cardNo.isBlank()) {
            maskedCardNo = "****";
        } else if (cardNo.length() <= 4) {
            maskedCardNo = "****";
        } else {
            maskedCardNo = "****" + cardNo.substring(cardNo.length() - 4);
        }

        return "PgSimulatorRequest[orderId=%s, cardType=%s, cardNo=%s, amount=%d, callbackUrl=%s]"
                .formatted(orderId, cardType, maskedCardNo, amount, callbackUrl);
    }
}
