package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;

@Tag(name = "Coupon V1 API", description = "쿠폰 API (대고객)")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿에 대해 발급 요청합니다. 로그인 필요.")
    ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "쿠폰 템플릿 ID", required = true)
        Long couponId
    );

    @Operation(summary = "내 쿠폰 목록", description = "보유한 쿠폰 목록을 조회합니다. 상태(AVAILABLE/USED/EXPIRED) 포함.")
    ApiResponse<Page<CouponV1Dto.IssuedCouponResponse>> getMyCoupons(
        @Parameter(description = "로그인 사용자 ID (X-Loopers-LoginId)", required = true)
        String loginId,
        @Parameter(description = "페이지 (0부터)")
        int page,
        @Parameter(description = "페이지 크기")
        int size
    );
}
