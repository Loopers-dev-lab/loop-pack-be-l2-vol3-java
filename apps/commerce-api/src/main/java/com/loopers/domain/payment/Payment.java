package com.loopers.domain.payment;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order_status", columnList = "order_id, status"),
        @Index(name = "idx_payment_user", columnList = "user_id"),
        @Index(name = "idx_payment_transaction_key", columnList = "transaction_key")
})
@Getter
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private final Long id = 0L;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 19)
    private String cardNo;

    @Column(name = "amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    protected Payment() {
    }

    public static Payment create(Long orderId, Long userId, CardType cardType, String cardNo, BigDecimal amount) {
        validateOrderId(orderId);
        validateUserId(userId);
        validateCardType(cardType);
        validateCardNo(cardNo);
        validateAmount(amount);

        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.userId = userId;
        payment.cardType = cardType;
        payment.cardNo = cardNo;
        payment.amount = amount;
        payment.status = PaymentStatus.PENDING;
        return payment;
    }

    public void markInProgress(String transactionKey) {
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 IN_PROGRESS로 변경할 수 있습니다");
        }
        validateTransactionKey(transactionKey);
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    public void markSucceeded(String transactionKey) {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 또는 IN_PROGRESS 상태에서만 SUCCEEDED로 변경할 수 있습니다");
        }
        if (transactionKey != null) {
            this.transactionKey = transactionKey;
        }
        this.status = PaymentStatus.SUCCEEDED;
    }

    public void markFailed(String reason) {
        if (this.status != PaymentStatus.PENDING && this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 또는 IN_PROGRESS 상태에서만 FAILED로 변경할 수 있습니다");
        }
        this.status = PaymentStatus.FAILED;
        this.failReason = reason;
    }

    public boolean isFinalized() {
        return this.status == PaymentStatus.SUCCEEDED || this.status == PaymentStatus.FAILED;
    }

    public boolean isOwnedBy(Long userId) {
        return this.userId.equals(userId);
    }

    @PrePersist
    protected void onCreate() {
        ZonedDateTime now = ZonedDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다");
        }
    }

    private static void validateCardType(CardType cardType) {
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다");
        }
    }

    private static void validateCardNo(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다");
        }
    }

    private void validateTransactionKey(String transactionKey) {
        if (transactionKey == null || transactionKey.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "거래 키는 필수입니다");
        }
    }
}
