package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.util.StringUtils;

import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "amount", nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "pg_payment_key")
    private String pgPaymentKey;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_synced_at")
    private ZonedDateTime lastSyncedAt;

    public PaymentModel(Long userId, Long orderId, Long amount, CardType cardType, String cardNo) {
        validate(userId, orderId, amount, cardType, cardNo);
        this.userId = userId;
        this.orderId = orderId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.REQUESTED;
    }

    public boolean isTerminal() {
        return this.status == PaymentStatus.SUCCESS
            || this.status == PaymentStatus.FAILED_LIMIT_EXCEEDED
            || this.status == PaymentStatus.FAILED_INVALID_CARD
            || this.status == PaymentStatus.FAILED;
    }

    public void markRequestAccepted(String paymentKey) {
        if (!StringUtils.hasText(paymentKey)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "PG 결제 키는 필수입니다.");
        }
        if (isTerminal()) {
            return;
        }
        this.pgPaymentKey = paymentKey;
        this.status = PaymentStatus.PENDING;
        this.lastError = null;
        this.lastSyncedAt = ZonedDateTime.now();
    }

    public void markSuccess(String paymentKey) {
        if (StringUtils.hasText(paymentKey)) {
            this.pgPaymentKey = paymentKey;
        }
        this.status = PaymentStatus.SUCCESS;
        this.lastError = null;
        this.lastSyncedAt = ZonedDateTime.now();
    }

    public void markFailed(PaymentStatus failedStatus, String reason) {
        if (failedStatus != PaymentStatus.FAILED_LIMIT_EXCEEDED
            && failedStatus != PaymentStatus.FAILED_INVALID_CARD
            && failedStatus != PaymentStatus.FAILED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 결제 실패 상태입니다.");
        }
        if (this.status == PaymentStatus.SUCCESS) {
            return;
        }
        this.status = failedStatus;
        this.lastError = StringUtils.hasText(reason) ? reason : null;
        this.lastSyncedAt = ZonedDateTime.now();
    }

    public void markPendingWithError(String reason) {
        if (isTerminal()) {
            return;
        }
        this.status = PaymentStatus.PENDING;
        this.lastError = StringUtils.hasText(reason) ? reason : null;
        this.lastSyncedAt = ZonedDateTime.now();
    }

    private void validate(Long userId, Long orderId, Long amount, CardType cardType, String cardNo) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
        if (amount == null || amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 1 이상이어야 합니다.");
        }
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드사는 필수입니다.");
        }
        if (!StringUtils.hasText(cardNo)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
    }
}
