package com.loopers.domain.coupon;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "issued_coupons", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"coupon_id", "user_id"})
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IssuedCoupon {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private IssuedCouponStatus status;

    @Column(name = "used_order_id")
    private Long usedOrderId;

    @Column(name = "issued_at", nullable = false)
    private ZonedDateTime issuedAt;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    @Column(name = "used_at")
    private ZonedDateTime usedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    public static IssuedCoupon create(Coupon coupon, Long userId) {
        IssuedCoupon issuedCoupon = new IssuedCoupon();
        issuedCoupon.couponId = coupon.getId();
        issuedCoupon.userId = userId;
        issuedCoupon.status = IssuedCouponStatus.AVAILABLE;
        issuedCoupon.issuedAt = ZonedDateTime.now();
        issuedCoupon.expiredAt = coupon.getValidUntil();
        issuedCoupon.discountType = coupon.getDiscountType();
        issuedCoupon.discountValue = coupon.getDiscountValue();
        issuedCoupon.minOrderAmount = coupon.getMinOrderAmount();
        issuedCoupon.maxDiscountAmount = coupon.getMaxDiscountAmount();
        return issuedCoupon;
    }
}
