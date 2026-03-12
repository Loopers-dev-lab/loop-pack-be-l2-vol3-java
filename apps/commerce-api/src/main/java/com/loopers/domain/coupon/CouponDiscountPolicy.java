package com.loopers.domain.coupon;

import com.loopers.domain.common.Money;

/**
 * 쿠폰 할인 정책 인터페이스.
 */
public interface CouponDiscountPolicy {

    /**
     * 할인 금액 계산.
     *
     * @param orderAmount 주문 금액
     * @param couponValue 쿠폰 값 (정액: 금액, 정률: 퍼센트*100)
     * @param maxDiscountAmount 최대 할인 금액 (정률 전용, nullable)
     * @return 할인 금액
     */
    Money calculateDiscount(Money orderAmount, Money couponValue, Integer maxDiscountAmount);
}
