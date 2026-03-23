package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;

import java.math.BigDecimal;

@Entity
@Table(
    name = "payments",
    indexes = {
        @Index(name = "idx_payment_order_id", columnList = "order_id"),
        @Index(name = "idx_payment_user_id", columnList = "user_id"),
        @Index(name = "idx_payment_pg_transaction_id", columnList = "pg_transaction_id")
    }
)
@Getter
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    @Column(name = "card_type", nullable = false, length = 50)
    private String cardType;

    @Column(name = "card_no", nullable = false, length = 30)
    private String cardNo;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    protected Payment() {
    }

    private Payment(Long orderId, Long userId, String cardType, String cardNo, BigDecimal amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long userId, String cardType, String cardNo, BigDecimal amount) {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("주문 ID는 필수입니다");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 ID는 필수입니다");
        if (cardType == null || cardType.isBlank()) throw new IllegalArgumentException("카드 종류는 필수입니다");
        if (cardNo == null || cardNo.isBlank()) throw new IllegalArgumentException("카드 번호는 필수입니다");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
        return new Payment(orderId, userId, cardType, cardNo, amount);
    }

    public void assignTransactionId(String pgTransactionId) {
        this.pgTransactionId = pgTransactionId;
    }

    public void markSuccess(String pgTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 결제만 성공 처리할 수 있습니다");
        }
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String pgTransactionId) {
        if (this.status != PaymentStatus.PENDING) {
            throw new IllegalStateException("PENDING 상태의 결제만 실패 처리할 수 있습니다");
        }
        if (pgTransactionId != null) {
            this.pgTransactionId = pgTransactionId;
        }
        this.status = PaymentStatus.FAILED;
    }

    public void markRefunded() {
        if (this.status != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("SUCCESS 상태의 결제만 환불 처리할 수 있습니다");
        }
        this.status = PaymentStatus.REFUNDED;
    }

    @Override
    protected void guard() {
        if (orderId == null || orderId <= 0) throw new IllegalArgumentException("주문 ID는 필수입니다");
        if (userId == null || userId <= 0) throw new IllegalArgumentException("사용자 ID는 필수입니다");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("결제 금액은 0보다 커야 합니다");
        if (status == null) throw new IllegalArgumentException("결제 상태는 필수입니다");
    }
}
