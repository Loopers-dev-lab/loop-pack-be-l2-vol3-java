package com.loopers.interfaces.api.payment;

import java.time.ZonedDateTime;

public class PaymentResponse {

    public record DiscountAppliedResponse(
            Long orderId,
            int subtotalAmount,
            int discountAmount,
            int pointUsedAmount,
            int shippingFee,
            int totalAmount
    ) {}

    public record PaymentResult(
            Long paymentId,
            Long orderId,
            String status,
            String paymentMethod,
            int requestedAmount,
            Integer approvedAmount,
            String pgTxnId,
            ZonedDateTime approvedAt
    ) {}
}
