package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class CouponFacade {
    private final CouponAppService couponAppService;

    public IssuedCoupon issueCoupon(Long couponId, Long userId) {
        return couponAppService.issueCoupon(couponId, userId);
    }

    public List<IssuedCouponInfo> getMyIssuedCoupons(Long userId) {
        return couponAppService.getMyIssuedCoupons(userId);
    }
}
