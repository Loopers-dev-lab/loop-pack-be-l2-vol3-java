package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.CouponService;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponService couponService;
    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.IssuedCouponResponse> issueCoupon(
        @RequestParam Long memberId,
        @PathVariable Long couponId
    ) {
        CouponInfo.IssuedCouponInfo info = couponService.issue(memberId, couponId);
        return ApiResponse.success(CouponV1Dto.IssuedCouponResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.MyCouponsResponse> getMyCoupons(@RequestParam Long memberId) {
        List<CouponInfo.IssuedCouponInfo> infos = couponFacade.getMyCoupons(memberId);
        return ApiResponse.success(CouponV1Dto.MyCouponsResponse.from(infos));
    }
}
