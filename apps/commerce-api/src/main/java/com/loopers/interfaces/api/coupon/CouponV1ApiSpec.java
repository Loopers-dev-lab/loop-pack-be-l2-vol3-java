package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 사용자 API")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "사용자에게 쿠폰을 발급합니다.")
    ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(Long memberId, Long couponId);

    @Operation(summary = "선착순 쿠폰 발급 요청 (비동기)", description = "발급 요청을 Kafka에 전달하고 eventId를 반환합니다. 결과는 polling으로 확인합니다.")
    ApiResponse<String> issueCouponAsync(Long memberId, Long couponId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "사용자의 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponV1Dto.MyCouponsResponse> getMyCoupons(Long memberId);
}
