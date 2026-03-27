package com.loopers.infrastructure.payment.dto;

public record PgPaymentResponse(
    String transactionKey,
    String orderId,
    String status,
    String failureReason
) {}
