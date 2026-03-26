package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Tag(name = "Coupon V1 API", description = "대고객 쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "인증된 사용자가 쿠폰을 발급받습니다.")
    ApiResponse<CouponV1Dto.CouponIssueRequestResponse> issue(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId
    );

    @Operation(summary = "쿠폰 발급 요청 조회", description = "비동기 쿠폰 발급 요청의 처리 상태를 조회합니다.")
    ApiResponse<CouponV1Dto.CouponIssueRequestResponse> getIssueRequest(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password,
        @Parameter(description = "쿠폰 발급 요청 ID", required = true) Long requestId
    );

    @Operation(summary = "내 쿠폰 목록", description = "인증된 사용자의 쿠폰 목록을 조회합니다.")
    ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @Parameter(description = "로그인 ID", required = true) String loginId,
        @Parameter(description = "비밀번호", required = true) String password
    );
}
