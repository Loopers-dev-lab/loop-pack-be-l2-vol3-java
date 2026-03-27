package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.Getter;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payments_order_id", columnNames = "orderId")
})
@Getter
public class PaymentModel extends BaseEntity {

    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false)
    private String cardType;

    @Column(nullable = false)
    private String cardNo;

    private String transactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    private String failReason;

    @Version
    private Long version;

    protected PaymentModel() {
    }

    public PaymentModel(Long orderId, Long memberId, int amount,
                        String cardType, String cardNo) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 필수입니다.");
        }
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (cardType == null || cardType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        this.orderId = orderId;
        this.memberId = memberId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.PENDING;
    }

    // PG에서 발급받은 transactionId 저장
    public void assignTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    // 상태 전이 메서드
    public void markSuccess(String transactionId) {
        validatePendingOrTimedOut();
        this.transactionId = transactionId;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String failReason) {
        validatePendingOrTimedOut();
        this.status = PaymentStatus.FAILED;
        this.failReason = failReason;
    }

    public void markTimedOut(String transactionId) {
        validatePending();
        this.transactionId = transactionId;
        this.status = PaymentStatus.TIMED_OUT;
    }

    // 상태 전이 검증 메서드들
    private void validatePendingOrTimedOut() {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.TIMED_OUT) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 상태가 " + this.status + "이므로 변경할 수 없습니다.");
        }
    }

    private void validatePending() {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 상태가 " + this.status + "이므로 타임아웃 처리할 수 없습니다.");
        }
    }
}
