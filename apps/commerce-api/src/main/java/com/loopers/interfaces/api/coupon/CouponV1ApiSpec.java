package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰을 발급받습니다.")
    ApiResponse<CouponV1Dto.IssueCouponResponse> issueCoupon(@Parameter(hidden = true) AuthenticatedUser authUser, Long couponId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "내가 보유한 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponV1Dto.MyCouponListResponse> getMyCoupons(@Parameter(hidden = true) AuthenticatedUser authUser);
}
