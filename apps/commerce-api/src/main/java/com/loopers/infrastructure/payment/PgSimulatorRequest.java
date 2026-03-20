package com.loopers.infrastructure.payment;

/**
 * PG-Simulator 결제 요청 Body (06 §2.1).
 * POST /api/v1/payments
 */
public record PgSimulatorRequest(
        Long orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
) {
}
