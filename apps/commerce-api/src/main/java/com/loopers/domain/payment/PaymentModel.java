package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.product.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

@Entity
@Table(name = "payments")
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "masked_card_no", nullable = false)
    private String maskedCardNo;

    @Embedded
    @AttributeOverride(name = "value", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "transaction_key", unique = true)
    private String transactionKey;

    @Column(name = "failure_reason")
    private String failureReason;

    protected PaymentModel() {}

    public PaymentModel(Long orderId, Long userId, CardType cardType, String maskedCardNo, Money amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.maskedCardNo = maskedCardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
        guard();
    }

    @Override
    protected void guard() {
        if (orderId == null) throw new CoreException(ErrorType.BAD_REQUEST, "주문 정보는 필수입니다.");
        if (userId == null) throw new CoreException(ErrorType.BAD_REQUEST, "사용자 정보는 필수입니다.");
        if (cardType == null) throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 필수입니다.");
        if (maskedCardNo == null || maskedCardNo.isBlank()) throw new CoreException(ErrorType.BAD_REQUEST, "카드번호는 필수입니다.");
        if (amount == null) throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
    }

    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    public void markSuccess() {
        if (this.status == PaymentStatus.SUCCESS) return;
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 성공 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        if (this.status == PaymentStatus.FAILED) return;
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 실패 처리할 수 있습니다.");
        }
        this.status = PaymentStatus.FAILED;
        this.failureReason = reason;
    }

    public Long orderId() { return orderId; }
    public Long userId() { return userId; }
    public CardType cardType() { return cardType; }
    public String maskedCardNo() { return maskedCardNo; }
    public Money amount() { return amount; }
    public PaymentStatus status() { return status; }
    public String transactionKey() { return transactionKey; }
    public String failureReason() { return failureReason; }
}
