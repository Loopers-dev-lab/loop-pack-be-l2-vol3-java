package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/v1/coupons")
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    // 쿠폰 발급 요청 (US-C01)
    @PostMapping("/{couponId}/issue")
    public ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(
            @LoginUser UserInfo loginUser,
            @PathVariable Long couponId)
    {
        return ApiResponse.success(
                CouponV1Dto.UserCouponResponse.from(
                        couponFacade.issue(loginUser.id(), couponId)));
    }

    // 선착순 쿠폰 발급 요청 (비동기)
    @PostMapping("/{couponTemplateId}/issue-request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ApiResponse<CouponV1Dto.CouponIssueResultResponse> requestIssueCoupon(
            @LoginUser UserInfo loginUser,
            @PathVariable Long couponTemplateId)
    {
        return ApiResponse.success(
                CouponV1Dto.CouponIssueResultResponse.from(
                        couponFacade.requestIssue(loginUser.id(), couponTemplateId)));
    }

    // 발급 결과 조회 (Polling)
    @GetMapping("/issue-results/{resultId}")
    public ApiResponse<CouponV1Dto.CouponIssueResultResponse> getIssueResult(
            @LoginUser UserInfo loginUser,
            @PathVariable Long resultId)
    {
        return ApiResponse.success(
                CouponV1Dto.CouponIssueResultResponse.from(
                        couponFacade.findIssueResult(loginUser.id(), resultId)));
    }

    // 내 쿠폰 목록 조회 (US-C02)
    @GetMapping("/my")
    public ApiResponse<CouponV1Dto.MyCouponListResponse> getMyIssuedCoupons(
            @LoginUser UserInfo loginUser)
    {
        return ApiResponse.success(
                CouponV1Dto.MyCouponListResponse.from(
                        couponFacade.findMyIssuedCoupons(loginUser.id())));
    }
}
