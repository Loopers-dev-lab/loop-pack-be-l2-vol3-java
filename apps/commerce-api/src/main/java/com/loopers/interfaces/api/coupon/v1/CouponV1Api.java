package com.loopers.interfaces.api.coupon.v1;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.IssueOwnedCouponUseCase;
import com.loopers.application.coupon.ReadCouponIssueStatusUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;
import com.loopers.interfaces.api.coupon.v1.CouponDto.CouponIssueStatusResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CouponV1Api implements CouponV1ApiSpec {

    private final IssueOwnedCouponUseCase issueCouponUseCase;
    private final ReadCouponIssueStatusUseCase readCouponIssueStatusUseCase;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(code = HttpStatus.ACCEPTED)
    @Override
    public ApiResponse<Void> issueCoupon(@LoginUser Long userId, @PathVariable Long couponId) {
        issueCouponUseCase.execute(couponId, userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/v1/coupons/{couponId}/issue-status")
    @Override
    public ApiResponse<CouponIssueStatusResponse> getCouponIssueStatus(
            @LoginUser Long userId,
            @PathVariable Long couponId
    ) {
        ReadCouponIssueStatusUseCase.Result result = readCouponIssueStatusUseCase.execute(couponId, userId);
        return ApiResponse.success(CouponIssueStatusResponse.from(result));
    }
}
