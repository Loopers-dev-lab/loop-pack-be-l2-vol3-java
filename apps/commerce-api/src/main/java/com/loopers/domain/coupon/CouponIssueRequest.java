package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.*;
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
    private CouponIssueRequestStatus status;

    @Column(name = "fail_reason", length = 200)
    private String failReason;

    protected CouponIssueRequest() {
    }

    public static CouponIssueRequest create(Long couponId, Long userId) {
        CouponIssueRequest request = new CouponIssueRequest();
        request.couponId = couponId;
        request.userId = userId;
        request.status = CouponIssueRequestStatus.PENDING;
        return request;
    }

    public void success() {
        this.status = CouponIssueRequestStatus.SUCCESS;
    }

    public void fail(String reason) {
        this.status = CouponIssueRequestStatus.FAILED;
        this.failReason = reason;
    }
}
