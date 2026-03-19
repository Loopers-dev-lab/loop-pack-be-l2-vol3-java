package com.loopers.domain.payment;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static lombok.AccessLevel.PROTECTED;

/**
 * 결제 엔티티 (06 §10.1).
 * orderId, pgTransactionId, status(PENDING/SUCCESS/FAILED/TIMEOUT), createdAt.
 */
@Entity
@Table(name = "payment", indexes = {
        @Index(name = "idx_payment_order_status", columnList = "order_id, status")
})
@Getter
@NoArgsConstructor(access = PROTECTED)
public class PaymentModel extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "pg_transaction_id", length = 255)
    private String pgTransactionId;

    @Column(name = "status", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private PaymentModel(Long orderId, String pgTransactionId, PaymentStatus status) {
        this.orderId = orderId;
        this.pgTransactionId = pgTransactionId;
        this.status = status;
    }

    /**
     * PENDING 결제를 생성한다. PG 접수 전 저장용.
     */
    public static PaymentModel createPending(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("주문 ID는 null일 수 없습니다.");
        }
        return new PaymentModel(orderId, null, PaymentStatus.PENDING);
    }

    /**
     * 상태를 SUCCESS로 전이한다. 콜백 처리 시 사용 (Phase 3).
     */
    public void markSuccess(String pgTransactionId) {
        this.pgTransactionId = pgTransactionId;
        this.status = PaymentStatus.SUCCESS;
    }

    /**
     * 상태를 FAILED로 전이한다.
     */
    public void markFailed() {
        this.status = PaymentStatus.FAILED;
    }

    /**
     * 상태를 TIMEOUT으로 전이한다. 폴링/복구 시 사용 (Phase 8).
     */
    public void markTimeout() {
        this.status = PaymentStatus.TIMEOUT;
    }

    public boolean isPending() {
        return status == PaymentStatus.PENDING;
    }
}
