package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "coupon_issue_requests")
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", nullable = false, unique = true, length = 36)
    private String requestId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueRequestStatus status;

    @Column(name = "failure_reason", length = 200)
    private String failureReason;

    protected CouponIssueRequest() {}

    private CouponIssueRequest(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueRequestStatus.PENDING;
    }

    public static CouponIssueRequest create(Long couponId, Long userId) {
        return new CouponIssueRequest(UUID.randomUUID().toString(), couponId, userId);
    }

    public void markSuccess() {
        this.status = CouponIssueRequestStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
    }

    public String getRequestId() { return requestId; }
    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }
    public CouponIssueRequestStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}