package com.loopers.interfaces.api.coupon;

import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.dto.FindMyCouponApiResDto;
import com.loopers.interfaces.api.coupon.dto.IssueCouponApiResDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Tag(name = "Coupon V1 API", description = "쿠폰 API 입니다.")
public interface CouponV1ApiSpec {

    @Operation(summary = "쿠폰 발급", description = "쿠폰 템플릿 ID를 기반으로 사용자에게 쿠폰을 발급합니다.")
    ApiResponse<IssueCouponApiResDto> issueCoupon(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            @Parameter(description = "쿠폰 템플릿 ID", required = true) Long couponId
    );

    @Operation(summary = "내 쿠폰 목록 조회", description = "로그인한 사용자의 쿠폰 목록을 조회합니다. AVAILABLE/USED/EXPIRED 상태를 포함합니다.")
    ApiResponse<Page<FindMyCouponApiResDto>> getMyCoupons(
            @Parameter(description = "로그인 ID", required = true) String loginId,
            @Parameter(description = "비밀번호", required = true) String password,
            Pageable pageable
    );
}
