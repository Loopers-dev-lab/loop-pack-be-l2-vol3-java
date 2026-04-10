package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupon_issue_requests")
@Getter
public class CouponIssueRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status;

    protected CouponIssueRequest() {}

    public CouponIssueRequest(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.PENDING;
    }

    public void markSuccess() {
        this.status = CouponIssueStatus.SUCCESS;
    }

    public void markFailed() {
        this.status = CouponIssueStatus.FAILED;
    }
}
