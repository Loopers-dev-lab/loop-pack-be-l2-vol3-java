package com.loopers.domain.payment;

public interface PgClient {

    PgPaymentResult requestPayment(PgPaymentCommand command);

    PgPaymentStatusResult getPaymentStatus(Long orderId, Long userId);
}
