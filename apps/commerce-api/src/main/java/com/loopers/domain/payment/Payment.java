package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.common.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Payment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "amount", nullable = false))
    private Money amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "pg_response_message")
    private String pgResponseMessage;

    private Payment(Long orderId, Long userId, String cardType, String cardNo, Money amount) {
        validateOrderId(orderId);
        validateUserId(userId);
        validateCardType(cardType);
        validateCardNo(cardNo);
        validateAmount(amount);
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static Payment create(Long orderId, Long userId, String cardType, String cardNo, Money amount) {
        return new Payment(orderId, userId, cardType, cardNo, amount);
    }

    public void complete(String transactionId, String pgMessage) {
        if (this.status == PaymentStatus.SUCCESS) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 결제 완료 처리할 수 있습니다.");
        }
        this.transactionId = transactionId;
        this.pgResponseMessage = pgMessage;
        this.status = PaymentStatus.SUCCESS;
    }

    public void fail(String pgMessage) {
        if (this.status == PaymentStatus.FAIL) {
            return;
        }
        if (this.status != PaymentStatus.PENDING) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PENDING 상태에서만 결제 실패 처리할 수 있습니다.");
        }
        this.pgResponseMessage = pgMessage;
        this.status = PaymentStatus.FAIL;
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private void validateCardType(String cardType) {
        if (cardType == null || cardType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 유형은 필수입니다.");
        }
    }

    private void validateCardNo(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
    }

    private void validateAmount(Money amount) {
        if (amount == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 필수입니다.");
        }
    }
}
