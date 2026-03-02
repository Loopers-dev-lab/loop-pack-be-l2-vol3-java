package com.loopers.domain.coupon.discount;

import com.loopers.domain.coupon.CouponType;
import com.loopers.domain.shared.Money;

/**
 * 쿠폰 할인 계산 전략 인터페이스.
 *
 * <p>{@link CouponType}별로 할인 금액 계산 방식을 정의한다.</p>
 */
public interface CouponDiscountStrategy {

    /**
     * 이 전략이 처리하는 쿠폰 타입을 반환한다.
     *
     * @return 쿠폰 타입
     */
    CouponType getType();

    /**
     * 할인 금액을 계산한다.
     *
     * @param discountValue    할인 값 (정액일 경우 금액, 정률일 경우 퍼센트)
     * @param orderTotal       주문 총액
     * @param maxDiscountPrice 최대 할인 금액
     * @return 계산된 할인 금액
     */
    Money calculate(Long discountValue, Money orderTotal, Money maxDiscountPrice);
}
