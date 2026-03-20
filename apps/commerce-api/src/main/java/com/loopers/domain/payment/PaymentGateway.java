package com.loopers.domain.payment;

public interface PaymentGateway {

    PaymentInfo requestPayment(Long userId, PaymentCommand.PgRequest command);

    PaymentInfo getPayment(Long userId, String transactionKey);

    PaymentInfo getPaymentByOrderId(Long userId, String orderId);
}
