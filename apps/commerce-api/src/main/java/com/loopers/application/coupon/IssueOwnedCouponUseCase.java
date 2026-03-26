package com.loopers.application.coupon;

import org.springframework.transaction.annotation.Transactional;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.OwnedCouponService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자에게 쿠폰을 발급합니다.
 */
@UseCase
@RequiredArgsConstructor
public class IssueOwnedCouponUseCase {

    private final CouponService couponService;
    private final OwnedCouponService ownedCouponService;

    /**
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     */
    @Transactional
    public void execute(Long couponId, Long userId) {
        Coupon coupon = couponService.issue(couponId);
        ownedCouponService.issue(coupon, userId);
    }
}
