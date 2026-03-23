package com.loopers.infrastructure.payment.entity;

import com.loopers.domain.payment.PaymentStatus;
import com.loopers.domain.payment.model.Payment;
import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "order_payment")
@SQLRestriction("deleted_at IS NULL")
public class PaymentEntity extends BaseEntity {

    @Column(nullable = false)
    private String orderId;

    @Column(nullable = false)
    private Long memberId;

    private String transactionKey;

    @Column(nullable = false)
    private String cardType;

    @Column(nullable = false)
    private String cardNo;

    @Column(nullable = false)
    private String amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String failReason;

    private PaymentEntity(String orderId, Long memberId, String cardType, String cardNo, String amount, PaymentStatus status) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
    }

    public static PaymentEntity toEntity(Payment payment) {
        return new PaymentEntity(
                payment.getOrderId(),
                payment.getMemberId(),
                payment.getCardType(),
                payment.getCardNo(),
                payment.getAmount(),
                payment.getStatus()
        );
    }

    public Payment toModel() {
        return Payment.reconstruct(
                this.getId(),
                this.orderId,
                this.memberId,
                this.transactionKey,
                this.cardType,
                this.cardNo,
                this.amount,
                this.status,
                this.failReason
        );
    }

    public void updateStatus(PaymentStatus status, String transactionKey, String failReason) {
        this.status = status;
        this.transactionKey = transactionKey;
        this.failReason = failReason;
    }
}
