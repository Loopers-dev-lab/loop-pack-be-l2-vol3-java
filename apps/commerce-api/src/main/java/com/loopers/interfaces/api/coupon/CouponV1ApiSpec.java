package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 사용자 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다.")
    ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(Long memberId, Long couponId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "사용자의 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponV1Dto.MyCouponsResponse> getMyCoupons(Long memberId);
}
