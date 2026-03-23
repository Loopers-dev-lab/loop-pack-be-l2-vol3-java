package com.loopers.domain.payment;

public record PgPaymentResponse(
    String transactionKey,
    String status,
    String reason
) {
    public boolean isPending() {
        return "PENDING".equals(status);
    }

    public boolean isSuccess() {
        return "SUCCESS".equals(status);
    }

    public boolean isFailed() {
        return "FAILED".equals(status);
    }
}
