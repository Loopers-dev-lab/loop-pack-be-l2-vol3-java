package com.loopers.application.coupon;

import com.loopers.application.coupon.CouponCommand.UpdateCouponCommand;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class UpdateCouponUseCase {

    private final CouponService couponService;

    public CouponResult execute(UpdateCouponCommand command) {
        Coupon coupon = couponService.update(
                command.couponId(),
                command.name(),
                command.discountValue(),
                command.maxDiscountPrice(),
                command.minOrderPrice(),
                command.expiredAt()
        );
        return CouponResult.from(coupon);
    }
}
