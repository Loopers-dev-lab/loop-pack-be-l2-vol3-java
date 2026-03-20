package com.loopers.domain.payment;

import java.util.Optional;

public interface PgClient {

    PgPaymentResponse requestPayment(PgPaymentRequest request);

    // 콜백 미수신 시: transactionKey로 PG에 직접 상태 조회
    Optional<PgPaymentResponse> getPaymentByTransactionKey(String transactionKey);

    // 타임아웃 후 transactionKey가 없을 때: orderId로 PG에 결제 여부 조회
    Optional<PgPaymentResponse> getPaymentByOrderId(String orderId);

    record PgPaymentRequest(
        String orderId,
        String cardType,
        String cardNo,
        Long amount,
        String callbackUrl
    ) {}

    record PgPaymentResponse(
        String transactionKey,
        String status,
        String reason
    ) {}
}
