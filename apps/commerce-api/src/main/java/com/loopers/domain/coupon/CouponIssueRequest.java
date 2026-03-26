package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon_issue_request")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "request_id", unique = true, nullable = false, updatable = false)
    private String requestId;

    @Column(name = "coupon_id", nullable = false, updatable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "status", nullable = false)
    @Enumerated(EnumType.STRING)
    private CouponIssueStatus status;

    @Column(name = "fail_reason")
    private String failReason;

    private CouponIssueRequest(String requestId, Long couponId, Long userId) {
        this.requestId = requestId;
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.PENDING;
    }

    public static CouponIssueRequest of(String requestId, Long couponId, Long userId) {
        return new CouponIssueRequest(requestId, couponId, userId);
    }

    public void markSuccess() {
        this.status = CouponIssueStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueStatus.FAILED;
        this.failReason = reason;
    }

    public String requestId() {
        return requestId;
    }

    public Long couponId() {
        return couponId;
    }

    public Long userId() {
        return userId;
    }

    public CouponIssueStatus status() {
        return status;
    }

    public String failReason() {
        return failReason;
    }
}
