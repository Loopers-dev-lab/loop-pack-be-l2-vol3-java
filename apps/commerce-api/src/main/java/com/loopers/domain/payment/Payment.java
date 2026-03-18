package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.util.Assert;

@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    @Version
    @Column(name = "version")
    private Long version;

    protected Payment() {}

    public Payment(Long orderId, Long userId, CardType cardType, String cardNo, int amount) {
        Assert.notNull(orderId, "주문 ID는 필수입니다.");
        Assert.notNull(userId, "유저 ID는 필수입니다.");
        Assert.notNull(cardType, "카드 종류는 필수입니다.");
        Assert.hasText(cardNo, "카드 번호는 필수입니다.");
        Assert.state(amount > 0, "결제 금액은 0보다 커야 합니다.");

        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    /**
     * PG 요청이 접수되어 transactionKey를 수신했을 때 호출.
     * PENDING → IN_PROGRESS
     */
    public void markInProgress(String transactionKey) {
        Assert.hasText(transactionKey, "트랜잭션 키는 필수입니다.");
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "PENDING 상태에서만 IN_PROGRESS로 전환할 수 있습니다. 현재 상태: " + this.status);
        }
        this.transactionKey = transactionKey;
        this.status = PaymentStatus.IN_PROGRESS;
    }

    /**
     * 콜백/조회를 통해 결제 성공이 확인되었을 때 호출.
     * IN_PROGRESS → PAID
     */
    public void markPaid() {
        if (this.status != PaymentStatus.IN_PROGRESS) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "IN_PROGRESS 상태에서만 결제 완료 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.PAID;
    }

    /**
     * 결제 실패 처리.
     * IN_PROGRESS → FAILED: PG 처리 후 실패 (한도초과, 잘못된 카드 등)
     * PENDING → FAILED: PG 미접수 확정 후 내부 실패 처리
     */
    public void markFailed(String reason) {
        if (this.status != PaymentStatus.IN_PROGRESS && this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "PENDING 또는 IN_PROGRESS 상태에서만 결제 실패 처리할 수 있습니다. 현재 상태: " + this.status);
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public Long getOrderId() { return orderId; }
    public Long getUserId() { return userId; }
    public CardType getCardType() { return cardType; }
    public String getCardNo() { return cardNo; }
    public int getAmount() { return amount; }
    public PaymentStatus getStatus() { return status; }
    public String getTransactionKey() { return transactionKey; }
    public String getFailureReason() { return failureReason; }
}
