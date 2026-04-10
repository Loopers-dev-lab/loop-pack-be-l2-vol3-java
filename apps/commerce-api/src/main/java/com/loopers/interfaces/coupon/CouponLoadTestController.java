package com.loopers.interfaces.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponLoadTestController {

    private final CouponFacade couponFacade;

    @PostMapping("/load-test/coupons/{couponId}/issue")
    public ApiResponse<Object> issue(
            @PathVariable Long couponId,
            @RequestHeader("X-User-Id") Long userId) {
        couponFacade.issueCoupon(couponId, userId);
        return ApiResponse.success();
    }
}
