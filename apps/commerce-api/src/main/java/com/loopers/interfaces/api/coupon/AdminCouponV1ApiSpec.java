package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Admin Coupon V1 API", description = "어드민 쿠폰 관리 API 입니다.")
public interface AdminCouponV1ApiSpec {

    @Operation(summary = "쿠폰 목록 조회", description = "쿠폰 템플릿 목록을 페이지 단위로 조회합니다.")
    ApiResponse<AdminCouponV1Dto.CouponPageResponse> getAllCoupons(int page, int size);

    @Operation(summary = "쿠폰 상세 조회", description = "특정 쿠폰 템플릿의 상세 정보를 조회합니다.")
    ApiResponse<AdminCouponV1Dto.CouponResponse> getCoupon(Long couponId);

    @Operation(summary = "쿠폰 등록", description = "새로운 쿠폰 템플릿을 등록합니다.")
    ApiResponse<AdminCouponV1Dto.CouponResponse> createCoupon(AdminCouponV1Dto.CreateCouponRequest request);

    @Operation(summary = "쿠폰 수정", description = "쿠폰 템플릿 정보를 수정합니다.")
    ApiResponse<AdminCouponV1Dto.CouponResponse> updateCoupon(Long couponId, AdminCouponV1Dto.UpdateCouponRequest request);

    @Operation(summary = "쿠폰 삭제", description = "쿠폰 템플릿을 삭제합니다. 이미 발급된 쿠폰은 만료 전까지 사용 가능합니다.")
    ApiResponse<Void> deleteCoupon(Long couponId);

    @Operation(summary = "쿠폰 발급 내역 조회", description = "특정 쿠폰의 발급 내역을 페이지 단위로 조회합니다.")
    ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse> getCouponIssues(Long couponId, int page, int size);
}
