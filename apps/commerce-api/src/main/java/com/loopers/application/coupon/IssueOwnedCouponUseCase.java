package com.loopers.application.coupon;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.OwnedCouponService;

import lombok.RequiredArgsConstructor;

/**
 * 사용자에게 쿠폰을 발급합니다.
 */
@UseCase
@RequiredArgsConstructor
public class IssueOwnedCouponUseCase {

    private final OwnedCouponService ownedCouponService;

    /**
     * @param couponId 발급할 쿠폰 ID
     * @param userId   발급 대상 사용자 ID
     */
    public void execute(Long couponId, Long userId) {
        ownedCouponService.issue(couponId, userId);
    }
}
