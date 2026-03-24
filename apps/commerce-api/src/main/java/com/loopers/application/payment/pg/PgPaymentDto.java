package com.loopers.application.payment.pg;

import com.loopers.application.payment.PaymentInfo;

import java.util.List;

public class PgPaymentDto {

    public static record PaymentRequest(
            String orderId,
            String cardType,
            String cardNo,
            Long amount,
            String callbackUrl
    ){
        public static PaymentRequest of(PaymentInfo paymentInfo, String callbackUrl) {
            return new PaymentRequest(
                    paymentInfo.orderId(),
                    paymentInfo.cardType(),
                    paymentInfo.cardNo(),
                    paymentInfo.amount(),
                    callbackUrl);
        }
    }

    public static record TransactionResponse(
            String transactionKey,
            TransactionStatus status,
            String reason
    ){}

    public static record TransactionListResponse(
            String orderId,
            List<TransactionResponse> transactions
    ) {}

    public enum TransactionStatus {
        PENDING,
        SUCCESS,
        FAILED
    }
}
