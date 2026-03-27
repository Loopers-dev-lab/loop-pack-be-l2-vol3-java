package com.loopers.infrastructure.pg.dto;

import java.time.ZonedDateTime;

public record PgPaymentResponse(
        String transactionId,
        String orderId,
        PgPaymentStatus status,
        Long amount,
        String cardType,
        String cardNo,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt,
        String failureReason
) {
    public boolean isSuccess() {
        return status == PgPaymentStatus.APPROVED;
    }

    public boolean isPending() {
        return status == PgPaymentStatus.PENDING;
    }

    public boolean isFailed() {
        return status == PgPaymentStatus.FAILED || status == PgPaymentStatus.REJECTED;
    }
}
