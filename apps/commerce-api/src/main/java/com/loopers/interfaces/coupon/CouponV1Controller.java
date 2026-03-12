package com.loopers.interfaces.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.dto.CouponResult;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.auth.Login;
import com.loopers.interfaces.auth.LoginUser;
import com.loopers.interfaces.coupon.dto.CouponV1Dto;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<Object> issue(
        @Login LoginUser loginUser,
        @PathVariable Long couponId
    ) {
        couponFacade.issueCoupon(couponId, loginUser.id());
        return ApiResponse.success();
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponV1Dto.OwnedCouponListResponse> myOwnedCoupons(
        @Login LoginUser loginUser
    ) {
        List<CouponResult.OwnedDetail> results =
                couponFacade.getMyOwnedCoupons(loginUser.id());
        return ApiResponse.success(
                new CouponV1Dto.OwnedCouponListResponse(
                        results.stream()
                                .map(CouponV1Dto.OwnedCouponResponse::from)
                                .toList()));
    }
}
