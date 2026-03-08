package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;
import java.util.Objects;

@Entity
@Table(name = "coupon_issues", uniqueConstraints = {
    @UniqueConstraint(name = "uk_coupon_issues_coupon_user", columnNames = {"coupon_id", "user_id"})
})
public class CouponIssue extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CouponIssueStatus status;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected CouponIssue() {}

    public CouponIssue(Long couponId, Long userId) {
        Objects.requireNonNull(couponId, "쿠폰 ID는 필수입니다.");
        Objects.requireNonNull(userId, "유저 ID는 필수입니다.");
        this.couponId = couponId;
        this.userId = userId;
        this.status = CouponIssueStatus.AVAILABLE;
    }

    public void use() {
        if (this.status != CouponIssueStatus.AVAILABLE) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용 가능한 상태의 쿠폰이 아닙니다.");
        }
        this.status = CouponIssueStatus.USED;
        this.usedAt = ZonedDateTime.now();
    }

    public void restore() {
        if (this.status != CouponIssueStatus.USED) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용된 쿠폰만 복원할 수 있습니다.");
        }
        this.status = CouponIssueStatus.AVAILABLE;
        this.usedAt = null;
    }

    public Long getCouponId() { return couponId; }
    public Long getUserId() { return userId; }
    public CouponIssueStatus getStatus() { return status; }
    public ZonedDateTime getUsedAt() { return usedAt; }
}
