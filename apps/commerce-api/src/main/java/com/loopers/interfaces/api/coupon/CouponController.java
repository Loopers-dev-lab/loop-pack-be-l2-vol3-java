package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.member.Member;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CouponController {
    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<CouponDto.IssuedCouponResponse> issueCoupon(
            @LoginUser Member member,
            @PathVariable Long couponId
    ) {
        IssuedCoupon issuedCoupon = couponFacade.issueCoupon(couponId, member.getId());
        return ApiResponse.success(CouponDto.IssuedCouponResponse.from(IssuedCouponInfo.from(issuedCoupon)));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<CouponDto.IssuedCouponListResponse> getMyIssuedCoupons(@LoginUser Member member) {
        List<IssuedCouponInfo> infos = couponFacade.getMyIssuedCoupons(member.getId());
        return ApiResponse.success(CouponDto.IssuedCouponListResponse.from(infos));
    }
}
