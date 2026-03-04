package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon API", description = "쿠폰 사용자 API")
public interface CouponApiV1Spec {

    // Command

    @Operation(
            summary = "쿠폰 발급",
            description = "사용자가 쿠폰을 발급받습니다. 1인 1매 제한."
    )
    ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
            AuthenticatedUser user,
            Long couponId
    );
}
