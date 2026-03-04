package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin API", description = "쿠폰 관리 API")
public interface CouponAdminApiV1Spec {

    // Command

    @Operation(
            summary = "쿠폰 등록",
            description = "새로운 쿠폰 템플릿을 등록합니다."
    )
    ApiResponse<CouponAdminV1Dto.CouponResponse> register(
            CouponRequest.Register request
    );
}
