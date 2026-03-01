package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "owned_coupon", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OwnedCoupon extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    private Coupon coupon;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OwnedCouponStatus status;

    public static OwnedCoupon create(Coupon coupon, Long userId) {
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.EXPIRED_COUPON);
        }
        OwnedCoupon ownedCoupon = new OwnedCoupon();
        ownedCoupon.coupon = coupon;
        ownedCoupon.userId = userId;
        ownedCoupon.status = OwnedCouponStatus.AVAILABLE;
        return ownedCoupon;
    }
}
