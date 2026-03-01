package com.loopers.application.coupon;

import com.loopers.application.coupon.CouponCommand.CreateCouponCommand;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class RegisterCouponUseCase {

    private final CouponService couponService;

    public CouponResult execute(CreateCouponCommand command) {
        Coupon coupon = couponService.create(
                command.name(),
                command.type(),
                command.discountValue(),
                command.maxDiscountPrice(),
                command.minOrderPrice(),
                command.expiredAt()
        );
        return CouponResult.from(coupon);
    }
}
