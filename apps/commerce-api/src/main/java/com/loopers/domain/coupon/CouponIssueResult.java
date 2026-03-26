package com.loopers.domain.coupon;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon_issue_results",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "coupon_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CouponIssueResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueResultStatus status;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    private CouponIssueResult(Long userId, Long couponId) {
        this.userId = userId;
        this.couponId = couponId;
        this.status = CouponIssueResultStatus.PROCESSING;
    }

    public static CouponIssueResult create(Long userId, Long couponId) {
        return new CouponIssueResult(userId, couponId);
    }

    public void markSuccess() {
        this.status = CouponIssueResultStatus.SUCCESS;
    }

    public void markFailed(String reason) {
        this.status = CouponIssueResultStatus.FAILED;
        this.failureReason = reason;
    }

    @PrePersist
    private void prePersist() {
        this.createdAt = ZonedDateTime.now();
        this.updatedAt = ZonedDateTime.now();
    }

    @PreUpdate
    private void preUpdate() {
        this.updatedAt = ZonedDateTime.now();
    }
}
