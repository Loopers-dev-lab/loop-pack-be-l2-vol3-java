package com.loopers.interfaces.api.coupon.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.v1.CouponDto.CouponIssueStatusResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 대고객 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(
            summary = "쿠폰 발급",
            description = "사용자에게 쿠폰을 발급합니다. 202 Accepted 응답 후 비동기로 처리됩니다."
    )
    ApiResponse<Void> issueCoupon(Long userId, Long couponId);

    @Operation(
            summary = "쿠폰 발급 상태 조회",
            description = "쿠폰 발급 요청의 처리 상태를 조회합니다. (PENDING/COMPLETED/FAILED)"
    )
    ApiResponse<CouponIssueStatusResponse> getCouponIssueStatus(Long userId, Long couponId);
}
