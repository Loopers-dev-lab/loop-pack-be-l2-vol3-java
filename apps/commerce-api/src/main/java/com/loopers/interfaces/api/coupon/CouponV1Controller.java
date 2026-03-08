package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponApplicationService couponApplicationService;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssueCouponResponse> issueCoupon(
        @AuthUser AuthenticatedUser authUser,
        @PathVariable Long couponId
    ) {
        CouponIssue issue = couponApplicationService.issueCoupon(couponId, authUser.userId());
        Coupon coupon = couponApplicationService.getCoupon(issue.getCouponId());
        return ApiResponse.success(CouponV1Dto.IssueCouponResponse.from(issue, coupon));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.MyCouponListResponse> getMyCoupons(@AuthUser AuthenticatedUser authUser) {
        List<CouponIssue> issues = couponApplicationService.getMyIssues(authUser.userId());

        Set<Long> couponIds = issues.stream()
            .map(CouponIssue::getCouponId)
            .collect(Collectors.toSet());

        List<Coupon> coupons = couponIds.stream()
            .map(couponApplicationService::getCoupon)
            .toList();

        return ApiResponse.success(CouponV1Dto.MyCouponListResponse.from(issues, coupons));
    }
}
