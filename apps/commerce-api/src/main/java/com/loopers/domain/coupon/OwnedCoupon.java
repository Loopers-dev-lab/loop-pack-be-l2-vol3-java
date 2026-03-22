package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.ZonedDateTime;
import java.util.Objects;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.shared.Money;
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

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Version
    private Long version;

    public static OwnedCoupon create(Coupon coupon, Long userId) {
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.EXPIRED_COUPON);
        }
        OwnedCoupon ownedCoupon = new OwnedCoupon();
        ownedCoupon.coupon = coupon;
        ownedCoupon.userId = userId;
        return ownedCoupon;
    }

    public Money calculateDiscount(Long userId, Money orderTotal, CouponDiscountProvider couponDiscountProvider) {
        validateUsable(userId, orderTotal);
        return coupon.calculateDiscount(orderTotal, couponDiscountProvider);
    }

    public void use() {
        validateCouponIsExpired();
        if (Objects.nonNull(usedAt)) {
            throw new CoreException(ErrorType.ALREADY_USED_COUPON);
        }
        this.usedAt = ZonedDateTime.now();
    }

    public void restore() {
        this.usedAt = null;
    }

    public String getStatus() {
        if (Objects.nonNull(usedAt)) {
            return "USED";
        }
        if (coupon.isExpired()) {
            return "EXPIRED";
        }
        return "AVAILABLE";
    }

    private void validateUsable(Long userId, Money orderTotal) {
        validateOwner(userId);
        validateCouponIsExpired();
        coupon.validateMinOrderPrice(orderTotal);
    }

    private void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(ErrorType.FORBIDDEN_COUPON_ACCESS);
        }
    }

    private void validateCouponIsExpired() {
        if (coupon.isExpired()) {
            throw new CoreException(ErrorType.EXPIRED_COUPON);
        }
    }
}
