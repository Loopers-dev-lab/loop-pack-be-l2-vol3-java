package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api-admin/v1/coupons")
@RequiredArgsConstructor
public class CouponAdminV1Controller implements CouponAdminApiV1Spec {

    private final CouponFacade couponFacade;

    // Command

    @PostMapping
    @Override
    public ApiResponse<CouponAdminV1Dto.CouponResponse> register(
            @RequestBody @Valid CouponRequest.Register request) {
        CouponInfo info = couponFacade.registerCoupon(request.toCommand());
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(info));
    }
}
