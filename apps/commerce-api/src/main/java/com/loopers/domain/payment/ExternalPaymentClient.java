package com.loopers.domain.payment;

public interface ExternalPaymentClient {

    void requestPayment(Long orderId, CardType cardType, String cardNo, long amount, String callbackUrl);

    ExternalPaymentResponse getPaymentByOrderId(Long orderId);
}