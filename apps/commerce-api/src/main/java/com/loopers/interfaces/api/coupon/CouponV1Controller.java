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

    // 선착순 발급: 요청만 접수하고 즉시 202 반환. 실제 발급은 Consumer가 처리.
    @PostMapping("/api/v1/coupons/{couponId}/issue/async")
    @Override
    public ApiResponse<String> issueCouponAsync(
        @RequestParam Long memberId,
        @PathVariable Long couponId
    ) {
        String eventId = couponService.requestIssue(memberId, couponId);
        return ApiResponse.success(eventId);
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.MyCouponsResponse> getMyCoupons(@RequestParam Long memberId) {
        List<CouponInfo.IssuedCouponInfo> infos = couponFacade.getMyCoupons(memberId);
        return ApiResponse.success(CouponV1Dto.MyCouponsResponse.from(infos));
    }
}
