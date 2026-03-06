package com.loopers.application.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.UserCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@RequiredArgsConstructor
@Component
public class CouponFacade {

    private final CouponService couponService;

    public CouponInfo issueCoupon(Long userId, Long couponTemplateId) {
        UserCoupon userCoupon = couponService.issue(userId, couponTemplateId);
        return CouponInfo.from(userCoupon);
    }

    public List<CouponInfo> getUserCoupons(Long userId) {
        return couponService.getUserCoupons(userId).stream()
            .map(CouponInfo::from)
            .toList();
    }
}
