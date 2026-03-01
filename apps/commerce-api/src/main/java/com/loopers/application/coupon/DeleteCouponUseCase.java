package com.loopers.application.coupon;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class DeleteCouponUseCase {

    private final CouponService couponService;

    public void execute(Long couponId) {
        couponService.delete(couponId);
    }
}
