package com.loopers.domain.collector;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CollectorCouponIssueRequestModel extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CollectorCouponIssueRequestStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    public CollectorCouponIssueRequestModel(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = CollectorCouponIssueRequestStatus.REQUESTED;
    }

    public void succeed() {
        this.status = CollectorCouponIssueRequestStatus.SUCCEEDED;
        this.failureReason = null;
    }

    public void fail(String reason) {
        this.status = CollectorCouponIssueRequestStatus.FAILED;
        this.failureReason = reason;
    }
}
