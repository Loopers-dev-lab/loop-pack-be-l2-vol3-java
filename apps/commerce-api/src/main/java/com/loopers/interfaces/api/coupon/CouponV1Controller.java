package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueRequestInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CouponV1Controller implements CouponApiV1Spec {

    private final CouponFacade couponFacade;

    // Command

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long couponId) {
        IssuedCouponInfo info = couponFacade.issueCoupon(couponId, user.id());
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(info));
    }

    @PostMapping("/api/v1/coupons/{couponId}/issue-async")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> issueAsync(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long couponId) {
        CouponIssueRequestInfo info = couponFacade.issueAsync(couponId, user.id());
        return ApiResponse.success(CouponV1Dto.CouponIssueRequestResponse.from(info));
    }

    // Query

    @GetMapping("/api/v1/coupons/{couponId}/issue-status")
    public ApiResponse<CouponV1Dto.CouponIssueRequestResponse> issueStatus(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long couponId) {
        CouponIssueRequestInfo info = couponFacade.getIssueStatus(couponId, user.id());
        return ApiResponse.success(CouponV1Dto.CouponIssueRequestResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<PageResponse<CouponV1Dto.MyCouponResponse>> myCoupons(
            @Valid CouponRequest.ListMyCoupons request,
            @AuthUser AuthenticatedUser user) {
        Page<IssuedCouponInfo> myCoupons = couponFacade.getMyCoupons(user.id(), request.toPageable());
        return ApiResponse.success(PageResponse.from(myCoupons, CouponV1Dto.MyCouponResponse::from));
    }
}
