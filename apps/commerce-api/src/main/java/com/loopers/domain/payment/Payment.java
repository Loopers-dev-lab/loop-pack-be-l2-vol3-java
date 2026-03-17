package com.loopers.domain.payment;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments")
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
}
