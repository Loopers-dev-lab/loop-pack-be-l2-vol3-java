package com.loopers.infrastructure.pg;

/**
 * PG에 전달하는 결제 요청 DTO.
 * PG 시뮬레이터 API 스펙: POST /api/v1/payments
 */
public record PgPaymentRequest(
    String orderId,
    String cardType,
    String cardNo,
    int amount,
    String callbackUrl
) {
    public static PgPaymentRequest of(Long orderId, String cardType, String cardNo,
                                       int amount, String callbackUrl) {
        return new PgPaymentRequest(
            String.valueOf(orderId), cardType, cardNo, amount, callbackUrl
        );
    }
}
