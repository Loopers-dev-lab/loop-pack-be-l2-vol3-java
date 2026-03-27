package com.loopers.domain.event;

public record PaymentRequestedEvent(
        Long paymentId,
        Long memberId,
        Long orderId,
        String orderNumber,
        String cardType,
        String cardNo,
        String amount,
        String callbackUrl
) {
}
