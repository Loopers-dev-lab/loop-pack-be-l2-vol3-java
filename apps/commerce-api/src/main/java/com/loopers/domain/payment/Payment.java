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

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_no", nullable = false, length = 25)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "external_transaction_id", length = 100)
    private String externalTransactionId;

    protected Payment() {}

    public Payment(Long orderId, Long memberId, CardType cardType, String cardNo, long amount,
        PaymentStatus status, String externalTransactionId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 비어있을 수 없습니다.");
        }
        if (memberId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "회원 ID는 비어있을 수 없습니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류는 비어있을 수 없습니다.");
        }
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 비어있을 수 없습니다.");
        }
        this.orderId = orderId;
        this.memberId = memberId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = status;
        this.externalTransactionId = externalTransactionId;
    }

    public Long getOrderId() {
        return orderId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public CardType getCardType() {
        return cardType;
    }

    public String getCardNo() {
        return cardNo;
    }

    public long getAmount() {
        return amount;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getExternalTransactionId() {
        return externalTransactionId;
    }

    public void complete(String transactionId) {
        this.status = PaymentStatus.COMPLETED;
        this.externalTransactionId = transactionId;
    }

    public void fail() {
        this.status = PaymentStatus.FAILED;
    }
}