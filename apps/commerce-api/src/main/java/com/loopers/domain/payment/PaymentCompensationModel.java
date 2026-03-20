package com.loopers.domain.payment;

import com.loopers.support.enums.CompensationStatus;
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

import java.time.LocalDateTime;

/**
 * 결제 보정 엔티티.
 * <p>
 * PG 결제 성공 후 stock commit 등 후속 처리가 실패했을 때,
 * 보정 대상을 기록하여 재시도 또는 수동 처리를 추적한다.
 * </p>
 */
@Entity
@Table(name = "payment_compensation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCompensationModel {

    private static final int DEFAULT_MAX_RETRIES = 3;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "compensation_id")
    private Long compensationId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CompensationStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    private PaymentCompensationModel(Long paymentId, Long orderId, String failureReason) {
        this.paymentId = paymentId;
        this.orderId = orderId;
        this.failureReason = failureReason;
        this.retryCount = 0;
        this.maxRetries = DEFAULT_MAX_RETRIES;
        this.status = CompensationStatus.PENDING;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * 보정 기록을 생성한다. status=PENDING, retryCount=0으로 초기화된다.
     *
     * @param paymentId     결제 ID
     * @param orderId       주문 ID
     * @param failureReason 실패 사유
     * @return 생성된 보정 엔티티
     */
    public static PaymentCompensationModel create(Long paymentId, Long orderId, String failureReason) {
        return new PaymentCompensationModel(paymentId, orderId, failureReason);
    }

    /**
     * 재시도 횟수를 증가시킨다. 최대 재시도 초과 시 MANUAL_REQUIRED로 전이한다.
     */
    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= this.maxRetries) {
            this.status = CompensationStatus.MANUAL_REQUIRED;
        }
    }

    /**
     * 보정 처리를 완료한다.
     */
    public void resolve() {
        this.status = CompensationStatus.RESOLVED;
        this.resolvedAt = LocalDateTime.now();
    }

    /**
     * 수동 처리 필요 상태로 전이한다.
     */
    public void markAsManualRequired() {
        this.status = CompensationStatus.MANUAL_REQUIRED;
    }
}
