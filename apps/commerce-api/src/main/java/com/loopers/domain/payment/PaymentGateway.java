package com.loopers.domain.payment;

public interface PaymentGateway {

    PaymentGatewayResponse requestPayment(String userId, PaymentGatewayRequest request);

    PaymentGatewayDetailResponse getTransaction(String userId, String transactionKey);

    PaymentGatewayOrderResponse getTransactionsByOrder(String userId, String orderId);

    record PaymentGatewayRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {
    }

    record PaymentGatewayResponse(
            String transactionKey,
            String status,
            String reason
    ) {
    }

    record PaymentGatewayDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
    }

    record PaymentGatewayOrderResponse(
            String orderId,
            java.util.List<PaymentGatewayResponse> transactions
    ) {
    }
}
