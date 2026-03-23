package com.loopers.infrastructure.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.Payment;
import com.loopers.domain.payment.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Comment;

@Entity
@Table(name = "payment")
@NoArgsConstructor
public class PaymentEntity extends BaseEntity {

    @Comment("주문 id (ref)")
    @Column(name = "ref_order_id", nullable = false, updatable = false)
    private Long refOrderId;

    @Comment("결제 상태")
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Comment("PG 거래 ID")
    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Comment("실패 사유")
    @Column(name = "fail_reason")
    private String failReason;

    private PaymentEntity(Long refOrderId, PaymentStatus status, String pgTransactionId, String failReason) {
        this.refOrderId = refOrderId;
        this.status = status;
        this.pgTransactionId = pgTransactionId;
        this.failReason = failReason;
    }

    public static PaymentEntity create(Payment payment) {
        return new PaymentEntity(
            payment.getRefOrderId(),
            payment.getStatus(),
            payment.getPgTransactionId(),
            payment.getFailReason()
        );
    }

    public Payment toDomain() {
        return Payment.restore(
            this.getId(),
            this.refOrderId,
            this.status,
            this.pgTransactionId,
            this.failReason,
            this.getCreatedAt()
        );
    }

    public void update(Payment payment) {
        this.status = payment.getStatus();
        this.pgTransactionId = payment.getPgTransactionId();
        this.failReason = payment.getFailReason();
    }

    public Long getRefOrderId() {
        return refOrderId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getPgTransactionId() {
        return pgTransactionId;
    }

    public String getFailReason() {
        return failReason;
    }
}
