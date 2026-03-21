package com.loopers.domain.payment.gateway;

public interface PaymentGateway {

    PgType getType();

    String getCircuitBreakerName();

    PgResult.Confirm confirm(PgCommand.Confirm command);

    PgResult.Cancel cancel(String paymentKey, PgCommand.Cancel command);

    PgResult.Query query(String paymentKey);
}
