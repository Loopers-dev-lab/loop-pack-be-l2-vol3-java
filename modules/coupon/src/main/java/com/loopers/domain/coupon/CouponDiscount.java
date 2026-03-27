package com.loopers.domain.coupon;

import java.math.BigDecimal;

/**
 * 쿠폰 적용 결과(할인 전 금액, 할인액, 최종 금액).
 * 주문 스냅샷 및 OrderFacade 전달용.
 */
public record CouponDiscount(
    BigDecimal beforeAmount,
    BigDecimal discountAmount,
    BigDecimal afterAmount
) {
    public CouponDiscount {
        if (beforeAmount == null || discountAmount == null || afterAmount == null) {
            throw new IllegalArgumentException("금액은 null일 수 없습니다.");
        }
        if (beforeAmount.signum() < 0 || discountAmount.signum() < 0 || afterAmount.signum() < 0) {
            throw new IllegalArgumentException("금액은 0 이상이어야 합니다.");
        }
    }
}
