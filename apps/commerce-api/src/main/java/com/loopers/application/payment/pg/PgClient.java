package com.loopers.application.payment.pg;

import java.util.Optional;

public interface PgClient {

    PgPaymentDto.TransactionResponse requestPayment(PgPaymentDto.PaymentRequest request);

    Optional<PgPaymentDto.TransactionResponse> getTransaction(String transactionKey);

    Optional<PgPaymentDto.TransactionListResponse> getTransactionsByOrderId(String orderId);
}
