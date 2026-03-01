package com.loopers.interfaces.api.coupon.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 Admin API", description = "쿠폰 어드민 API 입니다.")
public interface CouponV1AdminApiSpec {

    @Operation(
            summary = "쿠폰 등록",
            description = "새로운 쿠폰을 등록합니다."
    )
    ApiResponse<CouponDto.CreateCouponResponse> createCoupon(
            @Schema(description = "쿠폰 등록 요청 정보")
            CouponDto.CreateCouponRequest request
    );

    @Operation(
            summary = "쿠폰 목록 조회",
            description = "등록된 쿠폰 목록을 조회합니다."
    )
    ApiResponse<PageResponse<CouponDto.CouponResponse>> getCoupons(int page, int size);

    @Operation(
            summary = "쿠폰 상세 조회",
            description = "특정 쿠폰의 상세 정보를 조회합니다."
    )
    ApiResponse<CouponDto.CouponResponse> getCoupon(Long couponId);
}
