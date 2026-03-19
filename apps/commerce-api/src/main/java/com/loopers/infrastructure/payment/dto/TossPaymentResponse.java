package com.loopers.infrastructure.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TossPaymentResponse(
        String paymentKey,
        String orderId,
        String status,
        Long totalAmount,
        Long balanceAmount,
        String approvedAt,
        List<TossCancel> cancels
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TossCancel(
            Long cancelAmount,
            String cancelReason,
            String canceledAt,
            String cancelStatus
    ) {
    }

    public boolean isDone() {
        return "DONE".equals(status);
    }

    public boolean isCanceled() {
        return "CANCELED".equals(status) || "PARTIAL_CANCELED".equals(status);
    }
}
