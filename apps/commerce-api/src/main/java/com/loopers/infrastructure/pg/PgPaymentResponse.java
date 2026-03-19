package com.loopers.infrastructure.pg;

import java.util.List;

public record PgPaymentResponse<T>(
        Metadata meta,
        T data
) {
    public record Metadata(
            String result,
            String errorCode,
            String message
    ) {
    }

    public record TransactionResponse(
            String transactionKey,
            String status,
            String reason
    ) {
    }

    public record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {
    }

    public record OrderResponse(
            String orderId,
            List<TransactionResponse> transactions
    ) {
    }

    public boolean isSuccess() {
        return meta != null && "SUCCESS".equals(meta.result());
    }
}
