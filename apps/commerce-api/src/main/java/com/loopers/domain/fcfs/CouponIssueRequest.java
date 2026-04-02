package com.loopers.domain.fcfs;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * 쿠폰 발급 요청 도메인 모델.
 */
public class CouponIssueRequest {

    private final Long id;
    private final String requestId;
    private final Long couponId;
    private final Long userId;
    private CouponIssueRequestStatus status;
    private String failureReason;
    private final Instant createdAt;
    private Instant updatedAt;

    private CouponIssueRequest(
        Long id,
        String requestId,
        Long couponId,
        Long userId,
        CouponIssueRequestStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        this.id = id;
        this.requestId = Objects.requireNonNull(requestId, "requestId must not be null");
        this.couponId = Objects.requireNonNull(couponId, "couponId must not be null");
        this.userId = Objects.requireNonNull(userId, "userId must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static CouponIssueRequest create(Long couponId, Long userId) {
        return new CouponIssueRequest(
            null,
            UUID.randomUUID().toString(),
            couponId,
            userId,
            CouponIssueRequestStatus.PENDING,
            null,
            Instant.now(),
            Instant.now()
        );
    }

    public static CouponIssueRequest reconstitute(
        Long id,
        String requestId,
        Long couponId,
        Long userId,
        CouponIssueRequestStatus status,
        String failureReason,
        Instant createdAt,
        Instant updatedAt
    ) {
        return new CouponIssueRequest(id, requestId, couponId, userId, status, failureReason, createdAt, updatedAt);
    }

    public void markAsIssued() {
        this.status = CouponIssueRequestStatus.ISSUED;
        this.updatedAt = Instant.now();
    }

    public void markAsFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getRequestId() {
        return requestId;
    }

    public Long getCouponId() {
        return couponId;
    }

    public Long getUserId() {
        return userId;
    }

    public CouponIssueRequestStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
