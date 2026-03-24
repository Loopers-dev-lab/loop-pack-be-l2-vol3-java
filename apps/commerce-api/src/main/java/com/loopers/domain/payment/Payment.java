package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import java.util.Objects;

@Table(name = "payment")
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false, unique = true)
    private String orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Column(name = "status")
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Column(name = "reason")
    private String reason;

    private Payment(String orderId, Long userId, String cardType, String cardNo, Long amount) {
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment of (String orderId, Long userId, String cardType, String cardNo, Long amount) {
        if (orderId == null || orderId.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문번호는 비어있을 수 없습니다.");
        }

        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제자ID는 비어있을 수 없습니다.");
        }

        if (cardType == null || cardType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드타입은 비어있을 수 없습니다.");
        }

        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드번호는 비어있을 수 없습니다.");
        }

        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제금액은 양의 정수여야 합니다.");
        }

        return new Payment(orderId, userId, cardType, cardNo, amount);
    }

    public String orderId() {
        return orderId;
    }

    public Long userId() {
        return userId;
    }

    public String cardType() {
        return cardType;
    }

    public String cardNo() {
        return cardNo;
    }

    public Long amount() {
        return amount;
    }

    public PaymentStatus status() {
        return status;
    }

    public String transactionKey() {
        return transactionKey;
    }

    public String reason() {
        return reason;
    }

    public void applyPgResult(String transactionKey, PaymentStatus status, String reason) {
        if (this.status == PaymentStatus.SUCCESS) {
            if (status == PaymentStatus.FAILED) {
                return; // 이미 성공한 결제에 실패 콜백 → 무시
            }
            if (Objects.equals(this.transactionKey, transactionKey)) {
                return; // 동일 콜백 재발송 → 무시
            }
            throw new CoreException(ErrorType.CONFLICT, "중복 결제가 감지되었습니다.");
        }

        if (this.status == PaymentStatus.FAILED && status == PaymentStatus.FAILED) {
            return; // 중복 실패 콜백 → 무시
        }

        this.transactionKey = transactionKey;
        this.status = status;
        this.reason = reason;
    }

    public void reset(String cardType, String cardNo) {
        if (this.status == PaymentStatus.SUCCESS) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 완료된 결제입니다.");
        }
        if (this.status == PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 진행 중인 결제입니다.");
        }
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.transactionKey = null;
        this.reason = null;
        this.status = PaymentStatus.PENDING;
    }
}
