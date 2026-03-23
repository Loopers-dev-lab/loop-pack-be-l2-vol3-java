package com.loopers.domain.payment;

import java.util.List;

public interface PgClient {

    PgPaymentResponse requestPayment(PgPaymentRequest request);

    List<PgPaymentResponse> getPaymentsByOrderId(String userId, String orderId);
}
