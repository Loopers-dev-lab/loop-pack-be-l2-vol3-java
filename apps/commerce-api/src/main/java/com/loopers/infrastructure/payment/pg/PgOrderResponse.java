package com.loopers.infrastructure.payment.pg;

import com.loopers.domain.payment.OrderTransactionResult;

import java.util.List;

public record PgOrderResponse(
        String orderId,
        List<PgTransactionResponse> transactions
) {

    public OrderTransactionResult toDomain() {
        return new OrderTransactionResult(
                orderId,
                transactions.stream().map(PgTransactionResponse::toDomain).toList()
        );
    }
}
