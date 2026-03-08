package com.loopers.interfaces.api.coupon.v1;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Owned Coupon V1 API", description = "보유 쿠폰 대고객 API 입니다.")
public interface OwnedCouponV1ApiSpec {

    @Operation(
            summary = "내 쿠폰 목록 조회",
            description = "인증된 사용자가 보유한 쿠폰 목록을 페이지 단위로 조회합니다."
    )
    ApiResponse<PageResponse<OwnedCouponDto.MyOwnedCouponsResponse>> getMyOwnedCoupons(Long userId, int page, int size);
}
