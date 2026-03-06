package com.loopers.application.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.coupon.CouponCommand.CreateCouponCommand;
import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 새로운 쿠폰을 등록합니다.
 */
@UseCase
@RequiredArgsConstructor
public class RegisterCouponUseCase {

    private final CouponService couponService;

    /**
     * @param command 쿠폰 생성 커맨드
     * @return 등록된 쿠폰 정보
     */
    @Transactional
    public CouponResult execute(CreateCouponCommand command) {
        Coupon coupon = couponService.create(command.toCouponTerms());
        return CouponResult.from(coupon);
    }
}
