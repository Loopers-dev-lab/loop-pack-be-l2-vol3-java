package com.loopers.interfaces.api.payment;

public class PaymentRequest {

    public record ApplyDiscountRequest(Long issuedCouponId, int pointAmount) {}

    public record PayRequest(String paymentMethod, Long issuedCouponId) {}
}
