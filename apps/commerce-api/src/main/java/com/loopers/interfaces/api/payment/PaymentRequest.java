package com.loopers.interfaces.api.payment;

public class PaymentRequest {

    public record ApplyDiscountRequest(Long issuedCouponId, int pointAmount) {}

    public record PayRequest(String paymentMethod, Long issuedCouponId, String cardNo) {}

    /** PG 시뮬레이터가 보내는 콜백 요청 */
    public record PgCallbackRequest(
            String transactionKey,
            String orderId,
            String status,
            String reason
    ) {}
}
