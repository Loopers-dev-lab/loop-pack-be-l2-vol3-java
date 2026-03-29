package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupons")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private long discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private long minOrderAmount;

    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "issued_quantity", nullable = false)
    private int issuedQuantity;

    @Column(name = "valid_from", nullable = false)
    private ZonedDateTime validFrom;

    @Column(name = "valid_until", nullable = false)
    private ZonedDateTime validUntil;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    public static Coupon create(String name, DiscountType discountType, long discountValue,
                                long minOrderAmount, Long maxDiscountAmount, int totalQuantity,
                                ZonedDateTime validFrom, ZonedDateTime validUntil) {
        Coupon coupon = new Coupon();
        coupon.name = name;
        coupon.discountType = discountType;
        coupon.discountValue = discountValue;
        coupon.minOrderAmount = minOrderAmount;
        coupon.maxDiscountAmount = maxDiscountAmount;
        coupon.totalQuantity = totalQuantity;
        coupon.issuedQuantity = 0;
        coupon.validFrom = validFrom;
        coupon.validUntil = validUntil;
        coupon.deleted = false;
        return coupon;
    }

    public void validateIssuable() {
        ZonedDateTime now = ZonedDateTime.now();
        if (now.isBefore(validFrom) || now.isAfter(validUntil)) {
            throw new IllegalStateException("쿠폰 발급 기간이 아닙니다.");
        }
        if (this.issuedQuantity >= this.totalQuantity) {
            throw new IllegalStateException("쿠폰 발급 수량이 초과되었습니다.");
        }
        if (this.deleted) {
            throw new IllegalStateException("삭제된 쿠폰입니다.");
        }
    }

    public void issue() {
        validateIssuable();
        this.issuedQuantity++;
    }
}
