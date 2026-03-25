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
}
