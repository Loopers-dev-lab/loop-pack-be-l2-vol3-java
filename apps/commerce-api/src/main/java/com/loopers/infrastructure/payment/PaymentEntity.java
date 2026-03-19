package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.CardType;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Table(name = "payments")
public class PaymentEntity extends BaseEntity {

    @Getter
    @Column(name = "member_id", nullable = false)
    private String memberId;

    @Getter
    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Getter
    @Column(name = "pg_transaction_key")
    private String pgTransactionKey;

    @Column(name = "reason")
    private String reason;

    protected PaymentEntity() {
    }

    public PaymentEntity(String memberId, UUID orderId, CardType cardType, String cardNo, int amount,
                         PaymentStatus status, String pgTransactionKey, String reason) {
        this.memberId = memberId;
        this.orderId = orderId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.pgTransactionKey = pgTransactionKey;
        this.reason = reason;
    }

    public static PaymentEntity from(Payment payment) {
        return new PaymentEntity(
                payment.memberId(),
                payment.orderId(),
                payment.cardType(),
                payment.cardNo(),
                payment.amount(),
                payment.status(),
                payment.pgTransactionKey(),
                payment.reason()
        );
    }

    public void updateFrom(Payment payment) {
        this.cardType = payment.cardType();
        this.cardNo = payment.cardNo();
        this.amount = payment.amount();
        this.status = payment.status();
        this.pgTransactionKey = payment.pgTransactionKey();
        this.reason = payment.reason();
        if (payment.deletedAt() != null) {
            delete();
        }
    }

    public Payment toDomain() {
        return new Payment(
                getId(),
                memberId,
                orderId,
                cardType,
                cardNo,
                amount,
                status,
                pgTransactionKey,
                reason,
                getCreatedAt(),
                getUpdatedAt(),
                getDeletedAt()
        );
    }
}
