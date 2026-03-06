package com.loopers.application.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.CouponService;

import lombok.RequiredArgsConstructor;

/**
 * 어드민이 쿠폰을 삭제합니다.
 */
@UseCase
@RequiredArgsConstructor
public class DeleteCouponUseCase {

    private final CouponService couponService;

    /**
     * @param couponId 삭제할 쿠폰 ID
     */
    @Transactional
    public void execute(Long couponId) {
        couponService.delete(couponId);
    }
}
