package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "쿠폰 관리자 API")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "관리자가 쿠폰 템플릿 목록을 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponTemplateListResponse> getTemplates(int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "관리자가 쿠폰 템플릿을 상세 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> getTemplate(Long couponId);

    @Operation(summary = "쿠폰 템플릿 등록", description = "관리자가 새 쿠폰 템플릿을 등록합니다.")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> registerTemplate(
            CouponAdminV1Dto.CouponTemplateRegisterRequest request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "관리자가 쿠폰 템플릿을 수정합니다.")
    ApiResponse<CouponAdminV1Dto.CouponTemplateResponse> updateTemplate(
            Long couponId,
            CouponAdminV1Dto.CouponTemplateUpdateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "관리자가 쿠폰 템플릿을 삭제합니다. 발급된 쿠폰도 함께 soft delete됩니다.")
    ApiResponse<Void> deleteTemplate(Long couponId);

    @Operation(summary = "발급 내역 조회", description = "관리자가 특정 쿠폰 템플릿의 발급 내역을 조회합니다.")
    ApiResponse<CouponAdminV1Dto.UserCouponListResponse> getIssuesByTemplate(
            Long couponId, int page, int size);
}
