package com.loopers.domain.coupon;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "coupon_templates")
public class CouponTemplate extends BaseEntity {

    @Getter
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Getter
    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 10)
    private DiscountType discountType;

    @Getter
    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    @Getter
    @Column(name = "min_order_amount", nullable = false)
    private Long minOrderAmount;

    @Getter
    @Column(name = "max_discount_amount")
    private Long maxDiscountAmount;

    @Getter
    @Column(name = "valid_days", nullable = false)
    private Integer validDays;

    protected CouponTemplate() {}

    public CouponTemplate(String name, DiscountType discountType,
                          Long discountValue, Long minOrderAmount,
                          Long maxDiscountAmount, Integer validDays) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.BAD_REQUEST, "쿠폰 이름은 필수입니다");
        }
        if (discountType == null) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 타입은 필수입니다");
        }
        if (discountValue == null || discountValue < 1) {
            throw new CoreException(ErrorType.BAD_REQUEST, "할인 값은 1 이상이어야 합니다");
        }
        if (minOrderAmount == null || minOrderAmount < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액은 0 이상이어야 합니다");
        }
        if (validDays == null || validDays < 0) {
            throw new CoreException(ErrorType.BAD_REQUEST, "유효 기간은 0 이상이어야 합니다");
        }
        this.name = name;
        this.discountType = discountType;
        this.discountValue = discountValue;
        this.minOrderAmount = minOrderAmount;
        this.maxDiscountAmount = maxDiscountAmount;
        this.validDays = validDays;
    }

    public long calculateDiscount(long orderAmount) {
        if (orderAmount < this.minOrderAmount) {
            throw new CoreException(ErrorType.BAD_REQUEST, "최소 주문 금액을 충족하지 못했습니다");
        }

        long discount = switch (this.discountType) {
            case FIXED -> this.discountValue;
            case RATE -> orderAmount * this.discountValue / 100;
        };

        if (this.maxDiscountAmount != null) {
            discount = Math.min(discount, this.maxDiscountAmount);
        }

        return Math.min(discount, orderAmount);
    }
}
