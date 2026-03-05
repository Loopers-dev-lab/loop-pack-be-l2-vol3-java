package com.loopers.application.coupon;

import com.loopers.application.coupon.CouponCommand.UpdateCouponCommand;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 쿠폰 정보를 수정합니다.
 */
@UseCase
@RequiredArgsConstructor
public class UpdateCouponUseCase {

    private final CouponService couponService;

    /**
     * @param command 쿠폰 수정 커맨드
     * @return 수정된 쿠폰 정보
     */
    public CouponResult execute(UpdateCouponCommand command) {
        Coupon coupon = couponService.update(command.toModifyCoupon());
        return CouponResult.from(coupon);
    }
}
