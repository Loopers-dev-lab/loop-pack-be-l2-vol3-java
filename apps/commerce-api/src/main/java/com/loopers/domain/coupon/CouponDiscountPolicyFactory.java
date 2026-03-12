package com.loopers.domain.coupon;

import java.util.EnumMap;
import java.util.Map;

/**
 * 쿠폰 타입에 따른 할인 정책 팩토리.
 */
public class CouponDiscountPolicyFactory {

    private static final Map<CouponType, CouponDiscountPolicy> POLICIES = new EnumMap<>(CouponType.class);

    static {
        POLICIES.put(CouponType.FIXED, new FixedCouponDiscountPolicy());
        POLICIES.put(CouponType.RATE, new RateCouponDiscountPolicy());
    }

    private CouponDiscountPolicyFactory() {}

    /**
     * 쿠폰 타입에 맞는 할인 정책 반환.
     *
     * @param type 쿠폰 타입
     * @return 할인 정책
     */
    public static CouponDiscountPolicy getPolicy(CouponType type) {
        return POLICIES.get(type);
    }
}
