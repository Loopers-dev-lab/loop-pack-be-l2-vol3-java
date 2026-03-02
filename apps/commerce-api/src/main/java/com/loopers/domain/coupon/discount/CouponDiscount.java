package com.loopers.domain.coupon.discount;

import com.loopers.domain.shared.Money;

/**
 * 쿠폰 할인 적용 결과를 담는 레코드.
 *
 * @param discountAmount 할인 금액
 * @param ownedCouponId 적용된 보유 쿠폰 ID (쿠폰 미적용 시 null)
 */
public record CouponDiscount(
        Money discountAmount,
        Long ownedCouponId
) {

    /** 쿠폰 미적용 시 사용하는 상수. */
    public static final CouponDiscount NONE = new CouponDiscount(Money.ZERO, null);
}
