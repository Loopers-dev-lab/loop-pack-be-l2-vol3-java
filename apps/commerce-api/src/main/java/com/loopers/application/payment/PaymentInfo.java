package com.loopers.application.payment;

import com.loopers.domain.common.Money;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentInfo {
    private final Long paymentId;
    private final Long orderId;
    private final Long userId;
    private final String transactionId;
    private final String cardType;
    private final String cardNo;
    private final Money amount;
    private final PaymentStatus status;
    private final String pgResponseMessage;

    public static PaymentInfo from(Payment payment) {
        return PaymentInfo.builder()
                .paymentId(payment.getId())
                .orderId(payment.getOrderId())
                .userId(payment.getUserId())
                .transactionId(payment.getTransactionId())
                .cardType(payment.getCardType())
                .cardNo(payment.getCardNo())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .pgResponseMessage(payment.getPgResponseMessage())
                .build();
    }
}
