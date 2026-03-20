package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "payment")
public class Payment extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Column
    private String pgTransactionKey;

    @Column
    private String failReason;

    protected Payment() {}

    private Payment(Long orderId, Long memberId, Long amount) {
        this.orderId = orderId;
        this.memberId = memberId;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long memberId, Long amount) {
        if (orderId == null) throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        if (memberId == null) throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        if (amount == null || amount <= 0) throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        return new Payment(orderId, memberId, amount);
    }

    public void assignPgTransactionKey(String pgTransactionKey) {
        this.pgTransactionKey = pgTransactionKey;
    }

    public void markSuccess() {
        if (status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 성공 처리가 가능합니다.");
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        if (status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 실패 처리가 가능합니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    public void markTimeoutFailed() {
        this.status = PaymentStatus.TIMEOUT_FAILED;
    }

    public Long getOrderId() { return orderId; }
    public Long getMemberId() { return memberId; }
    public Long getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getPgTransactionKey() { return pgTransactionKey; }
    public String getFailReason() { return failReason; }
}
