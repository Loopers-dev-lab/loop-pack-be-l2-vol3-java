package com.loopers.domain.coupon;

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
 * 쿠폰 비동기 액션 (Transactional Outbox 패턴).
 * <p>
 * 주문 생성 TX 내에서 RESERVED 상태로 선점된 쿠폰의 후속 처리(CONFIRM/RESTORE)를
 * 비동기 Relay가 처리할 수 있도록 대기열 역할을 한다.
 * </p>
 */
@Entity
@Table(name = "coupon_pending_actions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponPendingActionModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "action_id")
    private Long actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private CouponActionType actionType;

    @Column(name = "user_coupon_id", nullable = false)
    private Long userCouponId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CouponActionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    private CouponPendingActionModel(CouponActionType actionType, Long userCouponId, Long orderId) {
        this.actionType = actionType;
        this.userCouponId = userCouponId;
        this.orderId = orderId;
        this.status = CouponActionStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
    }

    /**
     * CONFIRM 액션을 생성한다 (RESERVED → USED 확정).
     */
    public static CouponPendingActionModel confirm(Long userCouponId, Long orderId) {
        return new CouponPendingActionModel(CouponActionType.CONFIRM, userCouponId, orderId);
    }

    /**
     * RESTORE 액션을 생성한다 (RESERVED → AVAILABLE 복원).
     */
    public static CouponPendingActionModel restore(Long userCouponId, Long orderId) {
        return new CouponPendingActionModel(CouponActionType.RESTORE, userCouponId, orderId);
    }

    /**
     * 처리 완료 상태로 전이한다.
     */
    public void markDone() {
        this.status = CouponActionStatus.DONE;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 처리 실패 상태로 전이한다.
     */
    public void markFailed(String errorMessage) {
        this.status = CouponActionStatus.FAILED;
        this.processedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }

    /**
     * 취소 상태로 전이한다.
     */
    public void markCancelled() {
        this.status = CouponActionStatus.CANCELLED;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 재시도 횟수를 증가시킨다.
     */
    public void incrementRetry() {
        this.retryCount++;
    }
}
