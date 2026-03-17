package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.OrderTransactionResult;
import com.loopers.domain.payment.PaymentGateway;
import com.loopers.domain.payment.PaymentRequest;
import com.loopers.domain.payment.TransactionDetailResult;
import com.loopers.domain.payment.TransactionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentGatewayImpl implements PaymentGateway {

    private final PgPaymentHttpInterface pgClient;

    @Override
    public TransactionResult requestPayment(Long userId, PaymentRequest request) {
        PgApiResponse<PgTransactionResponse> response = pgClient.requestPayment(
                userId,
                PgPaymentRequest.from(request)
        );
        return response.data().toDomain();
    }

    @Override
    public TransactionDetailResult getTransaction(Long userId, String transactionKey) {
        PgApiResponse<PgTransactionDetailResponse> response = pgClient.getTransaction(
                userId,
                transactionKey
        );
        return response.data().toDomain();
    }

    @Override
    public OrderTransactionResult getTransactionsByOrder(Long userId, String orderId) {
        PgApiResponse<PgOrderResponse> response = pgClient.getTransactionsByOrder(
                userId,
                orderId
        );
        return response.data().toDomain();
    }
}
