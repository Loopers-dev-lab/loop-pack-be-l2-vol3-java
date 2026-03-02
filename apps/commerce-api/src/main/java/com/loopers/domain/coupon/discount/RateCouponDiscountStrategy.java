package com.loopers.domain.coupon.discount;

import org.springframework.stereotype.Component;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

/**
 * 정률 할인 쿠폰의 할인 계산 전략.
 *
 * <p>주문 총액에 할인율을 적용한 금액과 최대 할인 금액 중 작은 금액을 할인 금액으로 계산한다.</p>
 */
@Component
public class RateCouponDiscountStrategy implements CouponDiscountStrategy {

    @Override
    public CouponType getType() {
        return CouponType.RATE;
    }

    @Override
    public Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice) {
        Money calculated = Money.wons(orderTotal.getAmount() * discountValue / 100);
        return Money.min(calculated, maxDiscountPrice);
    }
}
