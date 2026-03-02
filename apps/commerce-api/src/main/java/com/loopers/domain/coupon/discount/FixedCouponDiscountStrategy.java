package com.loopers.domain.coupon.discount;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

/**
 * 정액 할인 쿠폰의 할인 계산 전략.
 *
 * <p>할인 값과 주문 총액 중 작은 금액을 할인 금액으로 계산한다.</p>
 */
@Component
public class FixedCouponDiscountStrategy implements CouponDiscountStrategy {

    @Override
    public CouponType getType() {
        return CouponType.FIXED;
    }

    @Override
    public Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice) {
        return Money.min(Money.wons(discountValue), orderTotal);
    }
}
