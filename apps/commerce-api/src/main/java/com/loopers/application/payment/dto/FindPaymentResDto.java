package com.loopers.application.payment.dto;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.model.Payment;

public record FindPaymentResDto(
        Long id,
        String orderId,
        String transactionKey,
        String cardType,
        String cardNo,
        String amount,
        PaymentStatus status,
        String failReason
) {
    public static FindPaymentResDto from(Payment payment) {
        return new FindPaymentResDto(
                payment.getId(),
                payment.getOrderId(),
                payment.getTransactionKey(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getStatus(),
                payment.getFailReason()
        );
    }
}
