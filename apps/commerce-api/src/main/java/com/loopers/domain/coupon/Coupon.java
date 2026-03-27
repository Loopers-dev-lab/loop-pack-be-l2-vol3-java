package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "coupon")
public class Coupon extends BaseEntity {

    @Column(name = "name")
    private String name;

    @Column(name = "coupon_type")
    @Enumerated(EnumType.STRING)
    private CouponType couponType;

    @Column(name = "value")
    private int value;

    @Column(name = "expired_at")
    private ZonedDateTime expiredAt;

    @Column(name = "total_quantity")
    private Integer totalQuantity;

    @Column(name = "remaining_quantity")
    private Integer remainingQuantity;

    private Coupon(String name, CouponType couponType, int value, Integer totalQuantity, ZonedDateTime expiredAt) {
        this.name = name;
        this.couponType = couponType;
        this.value = value;
        this.totalQuantity = totalQuantity;
        this.remainingQuantity = totalQuantity;
        this.expiredAt = expiredAt;
    }

    public static Coupon of(String name, String type, int value, ZonedDateTime expiredAt) {
        return of(name, type, value, null, expiredAt);
    }

    public static Coupon of(String name, String type, int value, int totalQuantity, ZonedDateTime expiredAt) {
        if (totalQuantity <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "발급 수량은 0보다 커야 합니다.");
        }
        return of(name, type, value, (Integer) totalQuantity, expiredAt);
    }

    private static Coupon of(String name, String type, int value, Integer totalQuantity, ZonedDateTime expiredAt) {
        if (!CouponType.isValid(type)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효하지 않은 쿠폰 타입입니다.");
        }
        CouponType couponType = CouponType.valueOf(type);
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (couponType == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인일 때 할인율이 100퍼센트를 초과할 수 없습니다.");
        }
        return new Coupon(name, couponType, value, totalQuantity, expiredAt);
    }

    public void update(String name, int value, ZonedDateTime expiredAt) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }

        if (this.couponType == CouponType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인일 때 할인율이 100퍼센트를 초과할 수 없습니다.");
        }

        this.name = name;
        this.value = value;
        this.expiredAt = expiredAt;
    }

    public String name() {
        return name;
    }

    public CouponType couponType() {
        return couponType;
    }

    public int value() {
        return value;
    }

    public ZonedDateTime expiredAt() {
        return expiredAt;
    }

    public Integer totalQuantity() {
        return totalQuantity;
    }

    public boolean isLimited() {
        return totalQuantity != null;
    }

    public boolean isExpired() {
        return expiredAt.isBefore(ZonedDateTime.now());
    }
}
