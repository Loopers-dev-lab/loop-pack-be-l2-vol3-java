package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Entity
@Table(name = "commerce_payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    private Payment(Long orderId, Long userId, BigDecimal amount, CardType cardType, String cardNo) {
        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.REQUESTED;
    }

    public static Payment create(Long orderId, Long userId, BigDecimal amount, CardType cardType, String cardNo) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
        return new Payment(orderId, userId, amount, cardType, cardNo);
    }

    public void markPending() {
        if (this.status != PaymentStatus.REQUESTED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING으로 전이할 수 없는 상태입니다: " + this.status);
        }
        this.status = PaymentStatus.PENDING;
    }

    public void markSuccess(String transactionKey) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 성공 처리할 수 없는 상태입니다: " + this.status);
        }
        this.status = PaymentStatus.SUCCESS;
        this.transactionKey = transactionKey;
    }

    public void markFailed(String failureReason) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 실패 처리할 수 없는 상태입니다: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = failureReason;
    }
}
