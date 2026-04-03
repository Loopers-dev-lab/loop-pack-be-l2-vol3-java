package com.loopers.interfaces.api.coupon;

import com.loopers.application.user.UserInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.support.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "쿠폰 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "인증된 회원이 쿠폰을 발급받습니다.")
    ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(
            @LoginUser UserInfo loginUser,
            Long couponId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "인증된 회원이 자신에게 발급된 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponV1Dto.MyCouponListResponse> getMyIssuedCoupons(
            @LoginUser UserInfo loginUser);

    @Operation(summary = "선착순 쿠폰 발급 요청", description = "선착순 쿠폰 발급을 요청합니다. 비동기로 처리되며, 결과는 polling으로 확인합니다.")
    ApiResponse<CouponV1Dto.CouponIssueResultResponse> requestIssueCoupon(
            @LoginUser UserInfo loginUser,
            Long couponTemplateId);

    @Operation(summary = "쿠폰 발급 결과 조회", description = "선착순 쿠폰 발급 요청의 처리 결과를 조회합니다.")
    ApiResponse<CouponV1Dto.CouponIssueResultResponse> getIssueResult(
            @LoginUser UserInfo loginUser,
            Long resultId);
}
