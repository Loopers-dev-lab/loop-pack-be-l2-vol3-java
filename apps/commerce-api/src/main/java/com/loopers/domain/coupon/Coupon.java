package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Entity
@Table(name = "coupon")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Coupon extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false)
    private DiscountType discountType;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "min_order_amount", nullable = false)
    private int minOrderAmount;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    public Coupon(String name, DiscountType discountType, int discountValue, int minOrderAmount, ZonedDateTime expiredAt) {
        validateDiscountValue(discountType, discountValue);
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.expiredAt = expiredAt;
    }

    public int calculateDiscount(int orderPrice) {
        if (discountType == DiscountType.FIXED) {
            return Math.min(discountValue, orderPrice);
        }
        return orderPrice * discountValue / 100;
    }

    public void validateUsable(int orderPrice, ZonedDateTime now) {
        if (now.isAfter(expiredAt)) {
            throw new CoreException(ErrorType.BAD_REQUEST, "만료된 쿠폰입니다.");
        }
        if (orderPrice < minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST,
                "최소 주문 금액(" + minOrderAmount + "원) 이상이어야 쿠폰을 사용할 수 있습니다.");
        }
    }

    public void changeName(String name) {
        this.name = name;
    }

    public void changeDiscount(DiscountType discountType, int discountValue) {
        validateDiscountValue(discountType, discountValue);
        this.discountType = discountType;
        this.discountValue = discountValue;
    }

    public void changeMinOrderAmount(int minOrderAmount) {
        this.minOrderAmount = minOrderAmount;
    }

    public void changeExpiredAt(ZonedDateTime expiredAt) {
        this.expiredAt = expiredAt;
    }

    private void validateDiscountValue(DiscountType type, int value) {
        if (value <= 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 0보다 커야 합니다.");
        }
        if (type == DiscountType.RATE && value > 100) {
            throw new CoreException(ErrorType.BAD_REQUEST, "정률 할인은 100%를 초과할 수 없습니다.");
        }
    }
}
