package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "owned_coupons", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OwnedCouponModel extends BaseEntity {

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "coupon_name", nullable = false)
    private String couponName;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private CouponDiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount")
    private Long minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    private OwnedCouponModel(Long couponId, String couponName,
                             CouponDiscountType discountType, long discountValue,
                             Long minOrderAmount, ZonedDateTime expiredAt, Long userId) {
        this.couponId = couponId;
        this.couponName = couponName;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
        this.userId = userId;
    }

    public static OwnedCouponModel create(CouponModel coupon, Long userId) {
        return new OwnedCouponModel(
                coupon.getId(), coupon.getName(),
                coupon.getDiscountType(), coupon.getDiscountValue(),
                coupon.getMinOrderAmount(), coupon.getExpiredAt(), userId);
    }

    public void use(Long userId, Long orderId) {
        validateUsable(userId);
        this.usedAt = ZonedDateTime.now();
        this.orderId = orderId;
    }

    public void restore() {
        if (this.orderId == null) {
            throw new CoreException(CouponErrorCode.NOT_RESTORABLE);
        }
        this.orderId = null;
        this.usedAt = null;
    }

    public long calculateDiscount(long orderAmount) {
        long discount = this.discountValue;
        if (this.discountType == CouponDiscountType.RATE) {
            discount = orderAmount * this.discountValue / 100;
        }
        return Math.min(discount, orderAmount);
    }

    public void validateMinOrderAmount(long orderAmount) {
        if (this.minOrderAmount != null && orderAmount < this.minOrderAmount) {
            throw new CoreException(CouponErrorCode.MIN_ORDER_AMOUNT_NOT_MET);
        }
    }

    public void validateUsable(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new CoreException(CouponErrorCode.NOT_OWNED);
        }
        if (this.orderId != null) {
            throw new CoreException(CouponErrorCode.ALREADY_USED);
        }
        if (this.expiredAt.isBefore(ZonedDateTime.now())) {
            throw new CoreException(CouponErrorCode.EXPIRED);
        }
    }

    public boolean isUsed() {
        return this.orderId != null;
    }

    public boolean isExpired() {
        return this.orderId == null
                && this.expiredAt.isBefore(ZonedDateTime.now());
    }

    public boolean isAvailable() {
        return this.orderId == null
                && !this.expiredAt.isBefore(ZonedDateTime.now());
    }

    public String getStatus() {
        if (isUsed()) return "USED";
        if (isExpired()) return "EXPIRED";
        return "AVAILABLE";
    }
}
