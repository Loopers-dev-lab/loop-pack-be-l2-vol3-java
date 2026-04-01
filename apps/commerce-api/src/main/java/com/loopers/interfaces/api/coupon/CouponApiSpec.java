package com.loopers.interfaces.api.coupon;

import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon API", description = "쿠폰 API")
public interface CouponApiSpec {

    @Operation(summary = "선착순 쿠폰 발급 요청", description = "비동기 FCFS 발급 요청. 202 응답 후 결과를 polling합니다.")
    ApiResponse<CouponResponse.CouponIssueRequestResponse> requestCouponIssue(
            @AuthUser User user, Long couponId);

    @Operation(summary = "쿠폰 발급 결과 조회", description = "비동기 발급 요청의 처리 결과를 polling합니다.")
    ApiResponse<CouponResponse.CouponIssueRequestResponse> getCouponIssueResult(
            @AuthUser User user, Long requestId);

    @Operation(summary = "내 쿠폰 목록 조회", description = "본인이 보유한 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponResponse.CouponListResponse> getMyCoupons(@AuthUser User user);

    @Operation(summary = "발급 가능한 쿠폰 목록 조회", description = "현재 발급 가능한 쿠폰 목록을 조회합니다.")
    ApiResponse<CouponResponse.AvailableCouponListResponse> getAvailableCoupons(@AuthUser User user);
}
