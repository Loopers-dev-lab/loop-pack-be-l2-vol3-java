package com.loopers.domain.payment;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, unique = true)
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardType cardType;

    @Column(nullable = false)
    private String cardNo;

    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String reason;

    public static Payment create(NewPayment newPayment) {
        Payment payment = new Payment();
        payment.userId = newPayment.userId();
        payment.orderId = newPayment.orderId();
        payment.transactionKey = newPayment.transactionKey();
        payment.cardType = newPayment.cardType();
        payment.cardNo = newPayment.cardNo();
        payment.amount = newPayment.amount();
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void update(PaymentStatus status, String reason) {
        if (isProcessed()) {
            throw new CoreException(ErrorType.PAYMENT_ALREADY_PROCESSED);
        }
        this.status = status;
        this.reason = reason;
    }

    public boolean isProcessed() {
        return this.status != PaymentStatus.PENDING;
    }
}
