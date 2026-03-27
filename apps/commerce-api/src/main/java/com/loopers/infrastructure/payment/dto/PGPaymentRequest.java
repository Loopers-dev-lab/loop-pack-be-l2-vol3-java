package com.loopers.infrastructure.payment.dto;

public record PGPaymentRequest(
            String orderId,
            String cardType,
            String cardNo,
            long amount,
            String callbackUrl) {
    }
