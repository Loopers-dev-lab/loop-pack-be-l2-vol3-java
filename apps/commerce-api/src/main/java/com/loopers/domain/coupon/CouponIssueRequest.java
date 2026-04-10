package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
public class CouponIssueRequest extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status = CouponIssueStatus.PENDING;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    public void markSuccess() {
        this.status = CouponIssueStatus.SUCCESS;
    }

    public void markFailed() {
        this.status = CouponIssueStatus.FAILED;
    }
}
