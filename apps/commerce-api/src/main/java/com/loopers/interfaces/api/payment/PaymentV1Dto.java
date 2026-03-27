package com.loopers.interfaces.api.payment;

import com.loopers.application.payment.PaymentInfo;

public class PaymentV1Dto {

    // 사용자가 결제 요청할 때 보내는 데이터
    public record PaymentRequest(
            String orderId,
            String cardType,
            String cardNo
    ) {
    }

    // PG가 콜백으로 보내주는 데이터 (TransactionInfo 필드명에 맞춤)
    public record CallbackRequest(
            String transactionKey,
            String orderId,
            String status,
            String reason
    ) {
    }

    // 결제 응답 — 사용자에게 내려주는 데이터
    public record PaymentResponse(
            Long paymentId,
            String transactionId,
            String orderId,
            String status,
            int amount
    ) {
        public static PaymentResponse from(PaymentInfo info) {
            return new PaymentResponse(
                    info.paymentId(),
                    info.transactionId(),
                    info.orderId(),
                    info.status(),
                    info.amount()
            );
        }
    }
}
