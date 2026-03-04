package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AuthUser;
import com.loopers.interfaces.api.auth.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/coupons")
@RequiredArgsConstructor
public class CouponV1Controller implements CouponApiV1Spec {

    private final CouponFacade couponFacade;

    // Command

    @PostMapping("/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
            @AuthUser AuthenticatedUser user,
            @PathVariable Long couponId) {
        IssuedCouponInfo info = couponFacade.issueCoupon(couponId, user.id());
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(info));
    }
}
