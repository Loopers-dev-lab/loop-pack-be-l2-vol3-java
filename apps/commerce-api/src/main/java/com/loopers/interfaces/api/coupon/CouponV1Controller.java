package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.application.coupon.UserCouponInfo;
import com.loopers.interfaces.api.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RequiredArgsConstructor
@RestController
public class CouponV1Controller implements CouponV1ApiSpec {

    private static final String HEADER_LOGIN_ID = "X-Loopers-LoginId";
    private static final String HEADER_LOGIN_PW = "X-Loopers-LoginPw";

    private final CouponFacade couponFacade;

    @PostMapping("/api/v1/coupons/{couponId}/issue")
    @Override
    public ApiResponse<CouponV1Dto.UserCouponResponse> issue(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password,
        @PathVariable Long couponId
    ) {
        UserCouponInfo info = couponFacade.issue(loginId, password, couponId);
        return ApiResponse.success(CouponV1Dto.UserCouponResponse.from(info));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<List<CouponV1Dto.UserCouponResponse>> getMyCoupons(
        @RequestHeader(HEADER_LOGIN_ID) String loginId,
        @RequestHeader(HEADER_LOGIN_PW) String password
    ) {
        List<CouponV1Dto.UserCouponResponse> response = couponFacade.getMyCoupons(loginId, password).stream()
            .map(CouponV1Dto.UserCouponResponse::from)
            .toList();
        return ApiResponse.success(response);
    }
}
