package com.loopers.interfaces.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.coupon.dto.CouponV1Dto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
        summary = "쿠폰 발급",
        description = "쿠폰을 발급받습니다."
    )
    ApiResponse<Object> issue(
        @Parameter(hidden = true) LoginUser loginUser,
        @Parameter(description = "쿠폰 ID", required = true) Long couponId
    );

    @Operation(
        summary = "내 쿠폰 목록 조회",
        description = "발급받은 쿠폰 목록을 조회합니다."
    )
    ApiResponse<CouponV1Dto.OwnedCouponListResponse> myOwnedCoupons(
        @Parameter(hidden = true) LoginUser loginUser
    );
}
