package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Getter
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "pg_order_code", nullable = false, unique = true)
    private String pgOrderCode;

    @Column(name = "pg_transaction_key")
    private String pgTransactionKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(nullable = false)
    private Long amount;

    protected Payment() {}

    private Payment(Long orderId, String pgOrderCode, CardType cardType, String cardNo, Long amount) {
        this.orderId = orderId;
        this.pgOrderCode = pgOrderCode;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, String pgOrderCode, CardType cardType, String cardNo, Long amount) {
        return new Payment(orderId, pgOrderCode, cardType, cardNo, amount);
    }

    public void assignPgTransaction(String pgTransactionId) {
        this.pgTransactionKey = pgTransactionId;
    }

}
