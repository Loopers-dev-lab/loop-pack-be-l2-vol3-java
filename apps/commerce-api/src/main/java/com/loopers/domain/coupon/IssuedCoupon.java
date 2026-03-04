package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(name = "issued_coupons")
@Getter
public class IssuedCoupon extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "used_at")
    private LocalDateTime usedAt;

    protected IssuedCoupon() {}

    private IssuedCoupon(Long couponId, Long userId) {
        this.couponId = couponId;
        this.userId = userId;
    }

    public static IssuedCoupon create(Long couponId, Long userId) {
        return new IssuedCoupon(couponId, userId);
    }

    public void use() {
        if (isUsed()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "이미 사용된 쿠폰입니다");
        }
        this.usedAt = LocalDateTime.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public boolean isDeleted() {
        return getDeletedAt() != null;
    }
}
