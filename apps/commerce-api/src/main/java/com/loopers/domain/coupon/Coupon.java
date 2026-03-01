package com.loopers.domain.coupon;

import java.time.ZonedDateTime;
import java.util.Objects;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.loopers.domain.BaseEntity;
import com.loopers.domain.shared.Money;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "coupon")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class Coupon extends BaseEntity {

    @Embedded
    private CouponName name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private CouponType type;

    @Column(name = "discount_value", nullable = false)
    private Long discountValue;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "max_discount_price"))
    private Money maxDiscountPrice;

    @Embedded
    @AttributeOverride(name = "amount", column = @Column(name = "min_order_price", nullable = false))
    private Money minOrderPrice;

    @Column(name = "expired_at", nullable = false)
    private ZonedDateTime expiredAt;

    public static Coupon create(
            String name,
            CouponType type,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
        validateType(type);
        validateDiscountValue(type, discountValue);
        validateMaxDiscountPrice(type, maxDiscountPrice);
        validateMinOrderPrice(minOrderPrice);
        validateExpiredAt(expiredAt);

        Coupon coupon = new Coupon();
        coupon.name = new CouponName(name);
        coupon.type = type;
        coupon.discountValue = discountValue;
        coupon.maxDiscountPrice = maxDiscountPrice != null ? Money.wons(maxDiscountPrice) : null;
        coupon.minOrderPrice = Money.wons(minOrderPrice);
        coupon.expiredAt = expiredAt;
        return coupon;
    }

    public void update(
            String name,
            Long discountValue,
            Long maxDiscountPrice,
            Long minOrderPrice,
            ZonedDateTime expiredAt
    ) {
        validateDiscountValue(this.type, discountValue);
        validateMaxDiscountPrice(this.type, maxDiscountPrice);
        validateMinOrderPrice(minOrderPrice);
        validateExpiredAt(expiredAt);

        this.name = new CouponName(name);
        this.discountValue = discountValue;
        this.maxDiscountPrice = maxDiscountPrice != null ? Money.wons(maxDiscountPrice) : null;
        this.minOrderPrice = Money.wons(minOrderPrice);
        this.expiredAt = expiredAt;
    }

    private static void validateType(CouponType type) {
        if (Objects.isNull(type)) {
            throw new CoreException(ErrorType.REQUIRED_COUPON_TYPE);
        }
    }

    private static void validateDiscountValue(CouponType type, Long discountValue) {
        if (Objects.isNull(discountValue)) {
            throw new CoreException(ErrorType.REQUIRED_DISCOUNT_VALUE);
        }
        if (discountValue < 1) {
            throw new CoreException(ErrorType.INVALID_DISCOUNT_VALUE);
        }
        if (type == CouponType.RATE && discountValue > 100) {
            throw new CoreException(ErrorType.INVALID_RATE_DISCOUNT_VALUE);
        }
    }

    private static void validateMaxDiscountPrice(CouponType type, Long maxDiscountPrice) {
        if (type == CouponType.RATE && Objects.isNull(maxDiscountPrice)) {
            throw new CoreException(ErrorType.REQUIRED_MAX_DISCOUNT_AMOUNT);
        }
    }

    private static void validateMinOrderPrice(Long minOrderPrice) {
        if (Objects.isNull(minOrderPrice)) {
            throw new CoreException(ErrorType.REQUIRED_MIN_ORDER_PRICE);
        }
    }

    private static void validateExpiredAt(ZonedDateTime expiredAt) {
        if (Objects.isNull(expiredAt)) {
            throw new CoreException(ErrorType.REQUIRED_EXPIRED_AT);
        }
        if (!expiredAt.isAfter(ZonedDateTime.now())) {
            throw new CoreException(ErrorType.INVALID_EXPIRED_AT);
        }
    }
}
