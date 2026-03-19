package com.loopers.domain.payment.gateway;

public interface PaymentGateway {

    PgType getType();

    String getCircuitBreakerName();

    PaymentConfirmResult confirm(PaymentConfirmCommand command);

    PaymentCancelResult cancel(String paymentKey, PaymentCancelCommand command);

    PaymentQueryResult query(String paymentKey);
}
