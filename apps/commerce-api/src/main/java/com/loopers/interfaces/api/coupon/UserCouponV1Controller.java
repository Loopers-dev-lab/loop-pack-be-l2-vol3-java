package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.user.AuthUserPrincipal;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.AuthUser;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class UserCouponV1Controller {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    public ApiResponse<UserCouponV1Dto.CouponResponse> issueCoupon(
        @AuthUser AuthUserPrincipal user,
        @PathVariable Long couponId
    ) {
        CouponInfo couponInfo = couponFacade.issueCoupon(user.getId(), couponId);
        return ApiResponse.success(UserCouponV1Dto.CouponResponse.from(couponInfo));
    }

    @GetMapping("/api/v1/users/me/coupons")
    public ApiResponse<UserCouponV1Dto.MyCouponListResponse> getMyCoupons(
        @AuthUser AuthUserPrincipal user
    ) {
        List<CouponInfo> couponInfos = couponFacade.getMyCoupons(user.getId());
        return ApiResponse.success(UserCouponV1Dto.MyCouponListResponse.from(couponInfos));
    }
}
