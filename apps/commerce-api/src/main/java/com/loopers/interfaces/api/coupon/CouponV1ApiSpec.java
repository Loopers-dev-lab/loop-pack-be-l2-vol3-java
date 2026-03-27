package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Coupon V1 API", description = "쿠폰 API (대고객)")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿에 대해 발급 요청합니다. 로그인 필요.")
    ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true) String loginId,
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId);

    @Operation(summary = "쿠폰 발급 요청(비동기)", description = "쿠폰 발급 요청을 Kafka 파이프라인으로 접수합니다. 로그인 필요.")
    ApiResponse<CouponV1Dto.CouponIssueRequestResponse> requestIssueCoupon(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true) String loginId,
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId);

    @Operation(summary = "쿠폰 발급 요청 상태 조회", description = "비동기 발급 요청의 처리 상태를 조회합니다. 본인 요청만 조회 가능.")
    ApiResponse<CouponV1Dto.CouponIssueRequestResponse> getIssueRequest(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true) String loginId,
            @Parameter(description = "발급 요청 ID (requestId)", required = true) String requestId);

    @Operation(summary = "내 쿠폰 목록", description = "보유한 쿠폰 목록을 조회합니다. 상태(AVAILABLE/USED/EXPIRED) 포함.")
    ApiResponse<CouponV1Dto.PagedIssuedCouponsResponse> getMyCoupons(
            @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true) String loginId,
            @Parameter(description = "페이지 (0부터)") int page,
            @Parameter(description = "페이지 크기") int size);
}
