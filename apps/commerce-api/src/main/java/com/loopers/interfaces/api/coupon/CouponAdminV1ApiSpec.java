package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.dto.CreateCouponTemplateApiReqDto;
import com.loopers.interfaces.api.coupon.dto.FindCouponTemplateApiResDto;
import com.loopers.interfaces.api.coupon.dto.FindIssuedCouponApiResDto;
import com.loopers.interfaces.api.coupon.dto.UpdateCouponTemplateApiReqDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 관리 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 등록", description = "새로운 쿠폰 템플릿을 등록합니다.")
    ApiResponse<FindCouponTemplateApiResDto> createTemplate(CreateCouponTemplateApiReqDto request);

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "쿠폰 템플릿 목록을 페이징 조회합니다.")
    ApiResponse<Page<FindCouponTemplateApiResDto>> getTemplates(Pageable pageable);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 상세 정보를 조회합니다.")
    ApiResponse<FindCouponTemplateApiResDto> getTemplate(
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿 정보를 수정합니다.")
    ApiResponse<FindCouponTemplateApiResDto> updateTemplate(
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId,
            UpdateCouponTemplateApiReqDto request);

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 삭제합니다. (Soft Delete)")
    ApiResponse<Void> deleteTemplate(
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 페이징 조회합니다.")
    ApiResponse<Page<FindIssuedCouponApiResDto>> getIssuedCoupons(
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId,
            Pageable pageable);
}
