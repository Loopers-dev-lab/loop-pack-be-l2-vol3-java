package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/coupons/{couponId}/issue")
    public ApiResponse<CouponV1Dto.UserCouponResponse> issueCoupon(
            @PathVariable Long couponId,
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        UserCouponInfo userCouponInfo = couponFacade.issueCoupon(userId, couponId);
        return ApiResponse.success(CouponV1Dto.UserCouponResponse.from(userCouponInfo));
    }

    @GetMapping("/users/me/coupons")
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
            @RequestHeader(value = "X-User-Id") Long userId
    ) {
        List<UserCouponInfo> userCoupons = couponFacade.getUserCoupons(userId);
        List<CouponV1Dto.UserCouponResponse> responses = userCoupons.stream()
                .map(CouponV1Dto.UserCouponResponse::from)
                .toList();
        return ApiResponse.success(responses);
    }
}
