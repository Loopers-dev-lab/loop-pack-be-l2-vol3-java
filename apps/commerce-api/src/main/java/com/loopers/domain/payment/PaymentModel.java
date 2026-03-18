package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.payment.vo.RefOrderId;
import com.loopers.infrastructure.jpa.converter.RefOrderIdConverter;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.regex.Pattern;

@Entity
@Table(
        name = "payments",
        uniqueConstraints = {@UniqueConstraint(name = "uq_payments_ref_order_id", columnNames = "ref_order_id")},
        indexes = {
                @Index(name = "idx_payments_pg_transaction_id", columnList = "pg_transaction_id"),
                @Index(name = "idx_payments_status", columnList = "status"),
                @Index(name = "idx_payments_ref_member_id", columnList = "ref_member_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentModel extends BaseEntity {

    private static final Pattern CARD_NO_PATTERN = Pattern.compile("^\\d{4}-\\d{4}-\\d{4}-\\d{4}$");

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Convert(converter = RefOrderIdConverter.class)
    @Column(name = "ref_order_id", nullable = false)
    private RefOrderId refOrderId;

    @Column(name = "ref_member_id", nullable = false)
    private Long refMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "card_type", nullable = false, length = 20)
    private CardType cardType;

    @Column(name = "card_number", nullable = false, length = 19)
    private String cardNumber;

    @Column(name = "amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "pg_transaction_id", length = 100)
    private String pgTransactionId;

    private PaymentModel(Long orderId, Long memberId, CardType cardType, String cardNumber, BigDecimal amount) {
        this.refOrderId = new RefOrderId(orderId);
        this.refMemberId = memberId;
        this.cardType = cardType;
        this.cardNumber = cardNumber;
        this.amount = amount;
        this.status = PaymentStatus.PENDING;
    }

    public static PaymentModel create(Long orderId, Long memberId, CardType cardType, String cardNumber, BigDecimal amount) {
        if (cardType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 종류가 비어 있습니다.");
        }
        if (cardNumber == null || !CARD_NO_PATTERN.matcher(cardNumber).matches()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "카드 번호는 xxxx-xxxx-xxxx-xxxx 형식이어야 합니다.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "결제 금액은 0보다 커야 합니다.");
        }
        return new PaymentModel(orderId, memberId, cardType, cardNumber, amount);
    }

    public void requested(String pgTransactionId) {
        validateTransition(PaymentStatus.REQUESTED);
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.REQUESTED;
    }

    public void complete() {
        validateTransition(PaymentStatus.COMPLETED);
        this.status = PaymentStatus.COMPLETED;
    }

    public void fail() {
        if (this.status == PaymentStatus.PENDING || this.status == PaymentStatus.REQUESTED) {
            this.status = PaymentStatus.FAILED;
            return;
        }
        throw new CoreException(ErrorType.BAD_REQUEST, "FAILED 전이 불가 상태: " + this.status);
    }

    private void validateTransition(PaymentStatus next) {
        boolean allowed = switch (this.status) {
            case PENDING -> next == PaymentStatus.REQUESTED;
            case REQUESTED -> next == PaymentStatus.COMPLETED;
            case COMPLETED, FAILED -> false;
        };
        if (!allowed) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                    "결제 상태 전이 불가: " + this.status + " → " + next);
        }
    }
}
