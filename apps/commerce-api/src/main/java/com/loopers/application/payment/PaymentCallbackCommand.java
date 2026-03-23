package com.loopers.application.payment;

public record PaymentCallbackCommand(
    Long orderId,
    String transactionKey,
    String status,
    String reason
) {
    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
