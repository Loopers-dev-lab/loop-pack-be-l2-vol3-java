package com.loopers.interfaces.api.admin;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 어드민 쿠폰 API 명세. (/api-admin/v1/coupons)
 */
@Tag(name = "Admin Coupon", description = "어드민 쿠폰 템플릿 관리 API")
public interface AdminCouponV1ApiSpec {

    @Operation(summary = "쿠폰 템플릿 목록 조회")
    ResponseEntity<ApiResponse<AdminCouponV1Dto.PagedCouponsResponse>> getCoupons(
        @Parameter(description = "페이지 (0부터)") int page,
        @Parameter(description = "페이지 크기") int size
    );

    @Operation(summary = "쿠폰 템플릿 상세 조회")
    ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> getCoupon(
        @PathVariable Long couponId
    );

    @Operation(summary = "쿠폰 템플릿 등록")
    ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> createCoupon(
        @RequestBody AdminCouponV1Dto.CreateCouponRequest request
    );

    @Operation(summary = "쿠폰 템플릿 수정")
    ResponseEntity<ApiResponse<AdminCouponV1Dto.CouponResponse>> updateCoupon(
        @PathVariable Long couponId,
        @RequestBody AdminCouponV1Dto.UpdateCouponRequest request
    );

    @Operation(summary = "쿠폰 템플릿 삭제")
    ResponseEntity<ApiResponse<Void>> deleteCoupon(
        @PathVariable Long couponId
    );

    @Operation(summary = "쿠폰 발급 내역 조회")
    ResponseEntity<ApiResponse<AdminCouponV1Dto.PagedIssuedCouponsResponse>> getCouponIssues(
        @PathVariable Long couponId,
        @Parameter(description = "페이지 (0부터)") int page,
        @Parameter(description = "페이지 크기") int size
    );
}
