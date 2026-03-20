package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 관리자 API")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회")
    ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse> getTemplates(int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세 조회")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> getTemplate(Long couponId);

    @Operation(summary = "쿠폰 템플릿 등록")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> createTemplate(CouponAdminV1Dto.CreateRequest request);

    @Operation(summary = "쿠폰 템플릿 수정")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> updateTemplate(Long couponId, CouponAdminV1Dto.UpdateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제")
    ApiResponse<Object> deleteTemplate(Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회")
    ApiResponse<CouponAdminV1Dto.IssuedCouponListResponse> getIssuedCoupons(Long couponId, int page, int size);
}
