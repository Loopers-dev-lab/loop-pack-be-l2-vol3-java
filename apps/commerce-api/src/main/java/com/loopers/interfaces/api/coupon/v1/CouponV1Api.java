package com.loopers.interfaces.api.coupon.v1;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.loopers.application.coupon.IssueOwnedCouponUseCase;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.LoginUser;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CouponV1Api implements CouponV1ApiSpec {

    private final IssueOwnedCouponUseCase issueCouponUseCase;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @ResponseStatus(code = HttpStatus.CREATED)
    @Override
    public ApiResponse<Void> issueCoupon(@LoginUser Long userId, @PathVariable Long couponId) {
        issueCouponUseCase.execute(couponId, userId);
        return ApiResponse.success(null);
    }
}
