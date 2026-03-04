package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
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

    @Operation(
            summary = "쿠폰 삭제",
            description = "쿠폰 템플릿을 삭제합니다. 미사용 발급 쿠폰도 함께 삭제됩니다."
    )
    ApiResponse<Void> delete(Long couponId);

    @Operation(
            summary = "쿠폰 수정",
            description = "쿠폰 템플릿 정보를 수정합니다."
    )
    ApiResponse<CouponAdminV1Dto.CouponResponse> update(
            Long couponId,
            CouponRequest.Update request
    );

    // Query

    @Operation(summary = "쿠폰 목록 조회")
    ApiResponse<PageResponse<CouponAdminV1Dto.CouponResponse>> list(CouponRequest.ListAll request);

    @Operation(summary = "쿠폰 상세 조회")
    ApiResponse<CouponAdminV1Dto.CouponResponse> detail(Long couponId);
}
