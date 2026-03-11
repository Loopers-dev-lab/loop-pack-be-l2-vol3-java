package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Coupon API", description = "어드민 쿠폰 관리 API")
public interface AdminCouponTemplateApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "쿠폰 템플릿 목록을 페이지네이션으로 조회합니다.")
    ApiResponse<AdminCouponTemplateResponse.TemplateListResponse> getTemplates(
            String ldap, int page, int size);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 상세 정보를 조회합니다.")
    ApiResponse<AdminCouponTemplateResponse.TemplateDetail> getTemplateDetail(
            String ldap, Long couponId);

    @Operation(summary = "쿠폰 템플릿 생성", description = "새로운 쿠폰 템플릿을 생성합니다.")
    ApiResponse<AdminCouponTemplateResponse.TemplateDetail> createTemplate(
            String ldap, AdminCouponTemplateRequest.CreateTemplateRequest request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿을 수정합니다.")
    ApiResponse<AdminCouponTemplateResponse.TemplateDetail> updateTemplate(
            String ldap, Long couponId, AdminCouponTemplateRequest.UpdateTemplateRequest request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 소프트 삭제합니다.")
    ApiResponse<Void> deleteTemplate(String ldap, Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 조회합니다.")
    ApiResponse<AdminCouponTemplateResponse.IssuedCouponListResponse> getIssuedCoupons(
            String ldap, Long couponId, int page, int size);
}
