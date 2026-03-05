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
import com.loopers.domain.coupon.discount.CouponDiscountProvider;
import com.loopers.domain.coupon.discount.CouponDiscountStrategy;
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

    public static Coupon create(CouponTerms terms) {
        validateType(terms.type());
        validateDiscountValue(terms.type(), terms.discountValue());
        validateMaxDiscountPrice(terms.type(), terms.maxDiscountPrice());
        validateMinOrderPrice(terms.minOrderPrice());
        validateExpiredAt(terms.expiredAt());

        Coupon coupon = new Coupon();
        coupon.name = new CouponName(terms.name());
        coupon.type = terms.type();
        coupon.discountValue = terms.discountValue();
        coupon.maxDiscountPrice = terms.maxDiscountPrice() != null ? Money.wons(terms.maxDiscountPrice()) : null;
        coupon.minOrderPrice = Money.wons(terms.minOrderPrice());
        coupon.expiredAt = terms.expiredAt();
        return coupon;
    }

    public void update(ModifyCoupon coupon) {
        validateDiscountValue(this.type, coupon.discountValue());
        validateMaxDiscountPrice(this.type, coupon.maxDiscountPrice());
        validateMinOrderPrice(coupon.minOrderPrice());
        validateExpiredAt(coupon.expiredAt());

        this.name = new CouponName(coupon.name());
        this.discountValue = coupon.discountValue();
        this.maxDiscountPrice = coupon.maxDiscountPrice() != null ? Money.wons(coupon.maxDiscountPrice()) : null;
        this.minOrderPrice = Money.wons(coupon.minOrderPrice());
        this.expiredAt = coupon.expiredAt();
    }

    public Money calculateDiscount(Money orderTotal, CouponDiscountProvider couponDiscountProvider) {
        CouponDiscountStrategy strategy = couponDiscountProvider.getStrategy(this.type);
        return strategy.calculate(discountValue, orderTotal, maxDiscountPrice);
    }

    public void validateMinOrderPrice(Money orderTotal) {
        if (orderTotal.isLessThan(this.minOrderPrice)) {
            throw new CoreException(ErrorType.COUPON_MIN_ORDER_PRICE_NOT_MET);
        }
    }

    public boolean isExpired() {
        return !expiredAt.isAfter(ZonedDateTime.now());
    }

    public Long getMaxDiscountPriceAmount() {
        if (maxDiscountPrice == null) {
            return null;
        }
        return maxDiscountPrice.getAmount();
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
