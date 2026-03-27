package com.loopers.application.coupon;

import com.loopers.domain.coupon.IssuedCoupon;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CouponFacade {
    private final CouponAppService couponAppService;
    private final CouponIssueRequestAppService couponIssueRequestAppService;

    public IssuedCoupon issueCoupon(Long couponId, Long userId) {
        return couponAppService.issueCoupon(couponId, userId);
    }

    public List<IssuedCouponInfo> getMyIssuedCoupons(Long userId) {
        return couponAppService.getMyIssuedCoupons(userId);
    }

    public String requestAsyncIssue(Long couponId, Long userId) {
        return couponIssueRequestAppService.requestCouponIssue(couponId, userId);
    }

    public Optional<String> getIssueRequestStatus(String requestId) {
        return couponIssueRequestAppService.getIssueRequestStatus(requestId);
    }
}
