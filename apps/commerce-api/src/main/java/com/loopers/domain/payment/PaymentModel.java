package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "masked_card_no", nullable = false)
    private String maskedCardNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id")
    private String pgTransactionId;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Version
    private Long version;

    private PaymentModel(Long orderId, int amount, CardType cardType, String maskedCardNo) {
        this.orderId = orderId;
        this.amount = amount;
        this.cardType = cardType;
        this.maskedCardNo = maskedCardNo;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentModel create(Long orderId, int amount,
                                      CardType cardType, String maskedCardNo) {
        return new PaymentModel(orderId, amount, cardType, maskedCardNo);
    }

    public void requested(String pgTransactionId) {
        validateTransition(PaymentStatus.REQUESTED);
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.REQUESTED;
    }

    public void complete() {
        validateTransition(PaymentStatus.COMPLETED);
        this.status = PaymentStatus.COMPLETED;
        this.approvedAt = LocalDateTime.now();
    }

    public void fail() {
        validateTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
    }

    public void fail(String failureCode, String failureMessage) {
        validateTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public boolean isCompleted() {
        return this.status == PaymentStatus.COMPLETED;
    }

    public boolean isFailed() {
        return this.status == PaymentStatus.FAILED;
    }

    public boolean isTerminal() {
        return isCompleted() || isFailed();
    }

    private void validateTransition(PaymentStatus next) {
        boolean allowed = switch (this.status) {
            case PENDING -> next == PaymentStatus.REQUESTED || next == PaymentStatus.FAILED;
            case REQUESTED -> next == PaymentStatus.COMPLETED || next == PaymentStatus.FAILED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed) {
            throw new CoreException(PaymentErrorCode.INVALID_STATUS_TRANSITION);
        }
    }
}
