package com.loopers.infrastructure.client;

public class PgPaymentDto {

    public record PaymentRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ) {}

    public record TransactionResponse(
            String transactionKey,
            String status,
            String reason
    ) {}

    public record TransactionDetailResponse(
            String transactionKey,
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String status,
            String reason
    ) {}

    public record OrderTransactionResponse(
            String orderId,
            java.util.List<TransactionSummary> transactions
    ) {}

    public record TransactionSummary(
            String transactionKey,
            String status,
            String reason
    ) {}

    // PG 공통 응답 래퍼: {"meta":{"result":"SUCCESS",...},"data":{...}}
    public record ApiResponse<T>(Meta meta, T data) {
        public boolean isSuccess() {
            return meta != null && "SUCCESS".equals(meta.result());
        }

        public record Meta(String result, String errorCode, String message) {}
    }
}
