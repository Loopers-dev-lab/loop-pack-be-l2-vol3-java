package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_order_id", columnList = "order_id"),
    @Index(name = "idx_payments_transaction_key", columnList = "transaction_key"),
    @Index(name = "idx_payments_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_payments_order_id", columnNames = "order_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "card_type")
    private String cardType;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "pg_provider")
    private String pgProvider;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    public static PaymentModel create(Long orderId, int amount, String cardType, String cardNo) {
        PaymentModel payment = new PaymentModel();
        payment.orderId = orderId;
        payment.amount = amount;
        payment.cardType = cardType;
        payment.cardNo = cardNo;
        payment.status = PaymentStatus.REQUESTED;
        return payment;
    }

    public void markPending(String transactionKey, String pgProvider) {
        validateTransition(PaymentStatus.PENDING);
        this.status = PaymentStatus.PENDING;
        this.transactionKey = transactionKey;
        this.pgProvider = pgProvider;
    }

    public void markPaid() {
        validateTransition(PaymentStatus.PAID);
        this.status = PaymentStatus.PAID;
    }

    public void markFailed(String reason) {
        validateTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public void markUnknown() {
        validateTransition(PaymentStatus.UNKNOWN);
        this.status = PaymentStatus.UNKNOWN;
    }

    private void validateTransition(PaymentStatus target) {
        if (!this.status.canTransitionTo(target)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "결제 상태를 " + this.status + "에서 " + target + "으로 변경할 수 없습니다.");
        }
    }
}
