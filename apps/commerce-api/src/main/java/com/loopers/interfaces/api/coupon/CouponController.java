package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponFacade;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class CouponController implements CouponApiSpec {

    private final CouponFacade couponFacade;

    public CouponController(CouponFacade couponFacade) {
        this.couponFacade = couponFacade;
    }

    @PostMapping("/api/v1/coupons/issue")
    @Override
    public ApiResponse<CouponResponse.IssueCouponResponse> issueCoupon(
            @AuthUser User user,
            @RequestBody CouponRequest.IssueCouponRequest request) {
        CouponFacade.IssueCouponResult result = couponFacade.issueCoupon(request.couponTemplateId(), user.getId());
        return ApiResponse.success(new CouponResponse.IssueCouponResponse(
                result.issuedCouponId(), result.status()));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponResponse.CouponListResponse> getMyCoupons(@AuthUser User user) {
        CouponFacade.CouponListResult result = couponFacade.getMyCoupons(user.getId());

        List<CouponResponse.IssuedCouponDetail> details = result.coupons().stream()
                .map(c -> new CouponResponse.IssuedCouponDetail(
                        c.issuedCouponId(), c.couponTemplateId(),
                        c.couponName(), c.discountType(),
                        c.discountValue(), c.maxDiscountAmount(),
                        c.status(), c.usedAt(), c.createdAt()))
                .toList();

        return ApiResponse.success(new CouponResponse.CouponListResponse(details));
    }
}
