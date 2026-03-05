package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Coupon Admin V1 API", description = "쿠폰 관리자 API 입니다.")
public interface CouponAdminV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회", description = "쿠폰 템플릿 목록을 조회합니다.")
    ApiResponse<Page<CouponAdminV1Dto.CouponResponse>> getAll(Pageable pageable);

    @Operation(summary = "쿠폰 템플릿 상세 조회", description = "쿠폰 템플릿 상세 정보를 조회합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
        @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId
    );

    @Operation(summary = "쿠폰 템플릿 등록", description = "쿠폰 템플릿을 등록합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> register(CouponAdminV1Dto.RegisterRequest request);

    @Operation(summary = "쿠폰 템플릿 수정", description = "쿠폰 템플릿을 수정합니다.")
    ApiResponse<CouponAdminV1Dto.CouponResponse> update(
        @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId,
        CouponAdminV1Dto.UpdateRequest request
    );

    @Operation(summary = "쿠폰 템플릿 삭제", description = "쿠폰 템플릿을 삭제합니다.")
    ApiResponse<Void> delete(
        @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId
    );

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰 템플릿의 발급 내역을 조회합니다.")
    ApiResponse<Page<CouponAdminV1Dto.CouponIssueResponse>> getIssues(
        @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId,
        Pageable pageable
    );
}
