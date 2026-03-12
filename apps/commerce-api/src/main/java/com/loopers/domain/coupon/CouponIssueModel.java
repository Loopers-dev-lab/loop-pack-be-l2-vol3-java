package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "coupon_issue", uniqueConstraints = {
    @UniqueConstraint(name = "uk_coupon_issue_user_coupon", columnNames = {"user_id", "coupon_id"})
})
public class CouponIssueModel extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "expired_at", nullable = false)
    private LocalDateTime expiredAt;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    @Column(name = "order_id")
    private Long orderId;

    protected CouponIssueModel() {
    }

    public CouponIssueModel(Long couponId, Long userId, LocalDateTime expiredAt) {
        this.couponId = couponId;
        this.userId = userId;
        this.expiredAt = expiredAt;
        guard();
    }

    @Override
    protected void guard() {
        if (couponId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 ID는 필수입니다.");
        }
        if (userId == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용자 ID는 필수입니다.");
        }
        if (expiredAt == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료일은 필수입니다.");
        }
    }

    public void use(Long orderId) {
        if (!isAvailable()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "사용할 수 없는 쿠폰입니다.");
        }
        this.usedAt = LocalDateTime.now();
        this.orderId = orderId;
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiredAt);
    }

    public boolean isAvailable() {
        return !isUsed() && !isExpired();
    }

    public CouponIssueStatus status() {
        if (isUsed()) return CouponIssueStatus.USED;
        if (isExpired()) return CouponIssueStatus.EXPIRED;
        return CouponIssueStatus.AVAILABLE;
    }

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "본인의 쿠폰만 사용할 수 있습니다.");
        }
    }

    public Long couponId() {
        return couponId;
    }

    public Long userId() {
        return userId;
    }

    public LocalDateTime expiredAt() {
        return expiredAt;
    }

    public LocalDateTime usedAt() {
        return usedAt;
    }

    public Long orderId() {
        return orderId;
    }

    public void setOrderId(Long orderId) {
        this.orderId = orderId;
    }
}
