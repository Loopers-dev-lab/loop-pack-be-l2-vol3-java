package com.loopers.domain.payment.model;

import com.loopers.domain.payment.PaymentStatus;
import lombok.Getter;

@Getter
public class Payment {

    private Long id;
    private String orderId;
    private Long memberId;
    private String transactionKey;
    private String cardType;
    private String cardNo;
    private String amount;
    private PaymentStatus status;
    private String failReason;

    private Payment(String orderId, Long memberId, String cardType, String cardNo, String amount) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(String orderId, Long memberId, String cardType, String cardNo, String amount) {
        return new Payment(orderId, memberId, cardType, cardNo, amount);
    }

    public static Payment reconstruct(Long id, String orderId, Long memberId, String transactionKey,
                                       String cardType, String cardNo, String amount,
                                       PaymentStatus status, String failReason) {
        Payment payment = new Payment(orderId, memberId, cardType, cardNo, amount);
        payment.id = id;
        payment.transactionKey = transactionKey;
        payment.status = status;
        payment.failReason = failReason;
        return payment;
    }

    public void markRequested(String transactionKey) {
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.REQUESTED;
    }

    public void markSuccess() {
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String failReason) {
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
    }
}
