package com.loopers.domain.payment;

public interface PaymentGateway {

    TransactionResult requestPayment(Long userId, PaymentRequest request);

    TransactionDetailResult getTransaction(Long userId, String transactionKey);

    OrderTransactionResult getTransactionsByOrder(Long userId, String orderId);
}
