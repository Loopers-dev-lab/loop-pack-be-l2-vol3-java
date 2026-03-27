package com.loopers.interfaces.payment.dto;

public record PgCallbackRequest(
        String transactionKey,
        String orderId,
        String cardType,
        String cardNo,
        long amount,
        String status,
        String reason) {
}
