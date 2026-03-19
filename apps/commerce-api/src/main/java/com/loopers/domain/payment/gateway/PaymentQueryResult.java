package com.loopers.domain.payment.gateway;

public record PaymentQueryResult(
        boolean found,
        boolean done,
        String status
) {
}
