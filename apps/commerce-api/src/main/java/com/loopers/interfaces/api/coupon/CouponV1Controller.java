package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponIssueResultInfo;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.CouponIssueResultResponse> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        CouponIssueResultInfo result = couponFacade.requestCouponIssue(userId, couponId);
        return ApiResponse.success(CouponV1Dto.CouponIssueResultResponse.from(result));
    }

    @GetMapping("/coupons/{couponId}/issue-result")
    public ApiResponse<CouponV1Dto.CouponIssueResultResponse> getIssueResult(
            @PathVariable Long couponId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        CouponIssueResultInfo result = couponFacade.getCouponIssueResult(userId, couponId);
        return ApiResponse.success(CouponV1Dto.CouponIssueResultResponse.from(result));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        List<UserCouponInfo> userCoupons = couponFacade.getUserCoupons(userId);
        List<CouponV1Dto.UserCouponResponse> responses = userCoupons.stream()
                .map(CouponV1Dto.UserCouponResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
