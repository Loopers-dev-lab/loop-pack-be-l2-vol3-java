package com.loopers.application.payment;

/**
 * PG 콜백 요청 파라미터 (06 §3, §10.4).
 * amount: PG 측 결제 금액. 있으면 주문 금액과 대조 (06-payment-change-issues §4.2).
 */
public record PaymentCallbackParam(
        Long orderId,
        boolean success,
        String pgTransactionId,
        String failureReason,
        Long amount
) {
}
