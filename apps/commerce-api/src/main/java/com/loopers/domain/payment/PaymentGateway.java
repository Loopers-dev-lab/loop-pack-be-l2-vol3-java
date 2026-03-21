package com.loopers.domain.payment;

public interface PaymentGateway {
    PgResult requestPayment(PaymentModel payment, String callbackUrl);
    PgResult getPaymentResult(String pgTransactionId, Long memberId);
    PgResult getPaymentByOrderId(Long orderId, Long memberId);
}
