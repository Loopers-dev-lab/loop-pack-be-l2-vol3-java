package com.loopers.application.coupon;

import com.loopers.application.shared.annotation.UseCase;
import com.loopers.domain.coupon.OwnedCouponService;

import lombok.RequiredArgsConstructor;

@UseCase
@RequiredArgsConstructor
public class IssueOwnedCouponUseCase {

    private final OwnedCouponService ownedCouponService;

    public void execute(Long couponId, Long userId) {
        ownedCouponService.issue(couponId, userId);
    }
}
