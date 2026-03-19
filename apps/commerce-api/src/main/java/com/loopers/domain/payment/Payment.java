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

import java.time.ZonedDateTime;

@Entity
@Table(name = "payments")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Payment extends BaseEntity {

    // 주문 ID (1:1 관계, UNIQUE 제약으로 중복 결제 방어)
    @Column(name = "order_id", nullable = false, unique = true, updatable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    // PG가 발급한 거래 키. 결제 요청 접수 후 할당
    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    // 결제 금액
    @Column(name = "amount", nullable = false)
    private int amount;

    @Column(name = "card_type", nullable = false)
    private String cardType;

    @Column(name = "card_no", nullable = false)
    private String cardNo;

    // PG가 전달한 결제 실패 사유
    @Column(name = "failure_reason")
    private String failureReason;

    // PG 응답 수신 시각 (콜백 또는 폴링으로 확인된 시각)
    @Column(name = "pg_responded_at")
    private ZonedDateTime pgRespondedAt;

    public Payment(Long orderId, Long userId, int amount, String cardType, String cardNo) {
        validateOrderId(orderId);
        validateUserId(userId);
        validateAmount(amount);
        validateCardType(cardType);
        validateCardNo(cardNo);

        this.orderId = orderId;
        this.userId = userId;
        this.amount = amount;
        this.cardType = cardType;
        this.cardNo = cardNo;
        this.status = PaymentStatus.PENDING;
    }

    // PG 접수 후 transactionKey 할당
    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    // 결제 성공 시 상태 전이
    public void markSuccess(String transactionKey) {
        validateTransition(PaymentStatus.SUCCESS);
        this.status = PaymentStatus.SUCCESS;
        this.transactionKey = transactionKey;
        this.pgRespondedAt = ZonedDateTime.now();
    }

    // 결제 실패 시 상태 전이
    public void markFailed(String transactionKey, String failureReason) {
        validateTransition(PaymentStatus.FAILED);
        this.status = PaymentStatus.FAILED;
        if (transactionKey != null) {
            this.transactionKey = transactionKey;
        }
        this.failureReason = failureReason;
        this.pgRespondedAt = ZonedDateTime.now();
    }

    // PG 응답 없음 (타임아웃) 시 상태 전이
    public void markTimeout() {
        validateTransition(PaymentStatus.TIMEOUT);
        this.status = PaymentStatus.TIMEOUT;
        this.failureReason = "결제 응답 시간 초과";
    }

    // 최종 상태 여부 확인 (멱등성 보장에 사용)
    public boolean isTerminal() {
        return this.status.isTerminal();
    }

    @Override
    protected void guard() {
        validateOrderId(this.orderId);
        validateUserId(this.userId);
        validateAmount(this.amount);
    }

    private void validateTransition(PaymentStatus target) {
        if (!this.status.canTransitTo(target)) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 상태를 변경할 수 없습니다: " + this.status + " → " + target);
        }
    }

    private void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유저 ID는 필수입니다.");
        }
    }

    private void validateAmount(int amount) {
        if (amount <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
    }

    private void validateCardType(String cardType) {
        if (cardType == null || cardType.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 타입은 필수입니다.");
        }
    }

    private void validateCardNo(String cardNo) {
        if (cardNo == null || cardNo.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 필수입니다.");
        }
    }
}
