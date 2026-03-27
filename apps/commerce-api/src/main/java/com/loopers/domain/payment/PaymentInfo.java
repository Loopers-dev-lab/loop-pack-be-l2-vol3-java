package com.loopers.domain.payment;

public record PaymentInfo(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        String amount,
        PgPaymentStatus status
) {
    public static PaymentInfo empty() {
        return new PaymentInfo(null, null, null, null, null, null);
    }

    public boolean hasTransactionKey() {
        return transactionKey != null;
    }
}
