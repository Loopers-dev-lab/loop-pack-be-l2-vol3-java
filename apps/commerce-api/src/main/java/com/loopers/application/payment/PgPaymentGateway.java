package com.loopers.application.payment;

import java.util.Optional;

public interface PgPaymentGateway {
    PgPaymentRequestResult requestPayment(PgPaymentRequest request);
    Optional<PgPaymentSnapshot> getPaymentByKey(String paymentKey);
    Optional<PgPaymentSnapshot> getPaymentByOrderId(Long orderId);
}
