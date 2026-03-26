package com.loopers.domain.payment;

public record ExternalPaymentResponse(String transactionId, boolean success) {
}