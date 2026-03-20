package com.loopers.domain.payment;

import com.loopers.domain.BaseStringIdEntity;
import com.loopers.support.enums.CardType;
import com.loopers.support.enums.PaymentStatus;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 결제 JPA 엔티티.
 * <p>
 * 주문(Order) 1건에 대해 N건의 결제 시도가 가능하다.
 * 상태 머신: {@code REQUESTED} → {@code SUCCESS} / {@code FAILED} / {@code CANCELLED}.
 * 모든 상태 전이는 CAS(Compare-And-Set) 방식으로 경쟁 조건을 방지한다.
 * </p>
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseStringIdEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "transaction_key")
    private String transactionKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false)
    private CardType cardType;

    @Column(name = "card_no_masked", nullable = false)
    private String cardNoMasked;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PaymentStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;

    private PaymentModel(Long orderId, Long userId, CardType cardType, String cardNo, BigDecimal amount) {
        validateOrderId(orderId);
        validateUserId(userId);
        validateAmount(amount);
        this.orderId = orderId;
        this.userId = userId;
        this.cardType = cardType;
        this.cardNoMasked = maskCardNumber(cardNo);
        this.amount = amount;
        this.status = PaymentStatus.REQUESTED;
    }

    /**
     * 결제 엔티티를 생성한다. status=REQUESTED로 초기화된다.
     *
     * @param orderId  주문 ID (필수)
     * @param userId   사용자 ID (필수)
     * @param cardType 카드 종류 (필수)
     * @param cardNo   카드 번호 (xxxx-xxxx-xxxx-xxxx 형식, 마스킹 처리됨)
     * @param amount   결제 금액 (0보다 커야 함)
     * @return 생성된 PaymentModel 인스턴스
     */
    public static PaymentModel create(Long orderId, Long userId, CardType cardType, String cardNo, BigDecimal amount) {
        return new PaymentModel(orderId, userId, cardType, cardNo, amount);
    }

    /**
     * 결제 상태를 전이한다. REQUESTED에서만 전이 가능.
     * SUCCESS로 전이 시 paidAt이 설정된다.
     *
     * @param newStatus 전이할 상태
     * @throws CoreException 전이 불가능한 상태인 경우 (PAYMENT_STATUS_INVALID)
     */
    public void transitTo(PaymentStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new CoreException(ErrorType.PAYMENT_STATUS_INVALID,
                    this.status + "에서 " + newStatus + "로 전이할 수 없습니다.");
        }
        this.status = newStatus;
        if (newStatus == PaymentStatus.SUCCESS) {
            this.paidAt = LocalDateTime.now();
        }
    }

    /**
     * PG에서 반환한 transactionKey를 설정한다.
     *
     * @param transactionKey PG 트랜잭션 식별자
     */
    public void assignTransactionKey(String transactionKey) {
        this.transactionKey = transactionKey;
    }

    /**
     * 결제 실패 사유를 설정한다.
     *
     * @param reason 실패 사유
     */
    public void assignFailureReason(String reason) {
        this.failureReason = reason;
    }

    /**
     * 카드번호를 마스킹한다. (1234-5678-9012-3456 → 1234-56**-****-3456)
     */
    private static String maskCardNumber(String cardNo) {
        if (cardNo == null || cardNo.length() != 19) {
            return cardNo;
        }
        return cardNo.substring(0, 7) + "**-****-" + cardNo.substring(15);
    }

    @Override
    protected void guard() {
        validateOrderId(this.orderId);
        validateUserId(this.userId);
        validateAmount(this.amount);
    }

    private static void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "주문 ID는 필수입니다.");
        }
    }

    private static void validateUserId(Long userId) {
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
    }

    private static void validateAmount(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
    }
}
