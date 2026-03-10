package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "issued_coupons")
public class IssuedCoupon extends BaseEntity {

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long couponId;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private LocalDateTime usedAt;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long userId, Long couponId, LocalDateTime expiresAt) {
        this.userId = userId;
        this.couponId = couponId;
        this.expiresAt = expiresAt;
    }

    public static IssuedCoupon create(Long userId, Long couponId, LocalDateTime expiresAt) {
        return new IssuedCoupon(userId, couponId, expiresAt);
    }

    public void markAsUsed() {
        this.usedAt = LocalDateTime.now();
    }

    public Status getStatus() {
        if (usedAt != null) {
            return Status.USED;
        }

        if (expiresAt.isBefore(LocalDateTime.now())) {
            return Status.EXPIRED;
        }

        return Status.AVAILABLE;
    }

    public void validateUsableBy(Long requestUserId) {
        Status status = getStatus();

        if (status == Status.USED) {
            throw new CoreException(ErrorType.COUPON_ALREADY_USED, "이미 사용된 쿠폰입니다.");
        }

        if (status == Status.EXPIRED) {
            throw new CoreException(ErrorType.COUPON_EXPIRED, "만료된 쿠폰입니다.");
        }

        if (!this.userId.equals(requestUserId)) {
            throw new CoreException(ErrorType.NOT_FOUND, "쿠폰을 찾을 수 없습니다.");
        }
    }

    public enum Status {
        AVAILABLE, USED, EXPIRED
    }
}
