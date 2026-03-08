package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.exception.CouponNotAvailableException;
import jakarta.persistence.*;
import lombok.Getter;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "coupon_issues",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_coupon_issue_coupon_user", columnNames = {"coupon_id", "user_id"})
    },
    indexes = {
        @Index(name = "idx_coupon_issue_coupon_id", columnList = "coupon_id"),
        @Index(name = "idx_coupon_issue_user_id", columnList = "user_id")
    }
)
@Getter
public class CouponIssue extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CouponIssueStatus status;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected CouponIssue() {
    }

    private CouponIssue(Long couponId, Long userId, ZonedDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.AVAILABLE;
        this.expiredAt = expiredAt;
    }

    public static CouponIssue create(Long couponId, Long userId, ZonedDateTime expiredAt) {
        if (couponId == null) {
            throw new IllegalArgumentException("쿠폰 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new IllegalArgumentException("사용자 ID는 필수입니다.");
        }
        if (expiredAt == null) {
            throw new IllegalArgumentException("만료일은 필수입니다.");
        }
        return new CouponIssue(couponId, userId, expiredAt);
    }

    public boolean isAvailable() {
        return status == CouponIssueStatus.AVAILABLE && !isExpired();
    }

    public boolean isExpired() {
        return ZonedDateTime.now().isAfter(expiredAt);
    }

    public void use() {
        if (!isAvailable()) {
            throw new CouponNotAvailableException();
        }
        this.status = CouponIssueStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public void revoke() {
        this.status = CouponIssueStatus.AVAILABLE;
        this.usedAt = null;
    }
}
