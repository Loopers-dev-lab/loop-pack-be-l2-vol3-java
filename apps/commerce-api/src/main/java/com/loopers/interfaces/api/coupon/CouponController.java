package com.loopers.interfaces.api.coupon;

import com.loopers.domain.coupon.CouponService;
import com.loopers.domain.coupon.CouponTemplate;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.domain.user.User;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.support.auth.AuthUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
public class CouponController implements CouponApiSpec {

    private final CouponService couponService;

    public CouponController(CouponService couponService) {
        this.couponService = couponService;
    }

    @PostMapping("/api/v1/coupons/issue")
    @Override
    public ApiResponse<CouponResponse.IssueCouponResponse> issueCoupon(
            @AuthUser User user,
            @RequestBody CouponRequest.IssueCouponRequest request) {
        IssuedCoupon issued = couponService.issue(request.couponTemplateId(), user.getId());
        return ApiResponse.success(new CouponResponse.IssueCouponResponse(
                issued.getId(), issued.getStatus().name()));
    }

    @GetMapping("/api/v1/users/me/coupons")
    @Override
    public ApiResponse<CouponResponse.CouponListResponse> getMyCoupons(@AuthUser User user) {
        List<IssuedCoupon> coupons = couponService.getUserCoupons(user.getId());

        Set<Long> templateIds = coupons.stream()
                .map(IssuedCoupon::getCouponTemplateId)
                .collect(Collectors.toSet());

        Map<Long, CouponTemplate> templateMap = templateIds.stream()
                .collect(Collectors.toMap(id -> id, couponService::getTemplate));

        List<CouponResponse.IssuedCouponDetail> details = coupons.stream()
                .map(c -> {
                    CouponTemplate t = templateMap.get(c.getCouponTemplateId());
                    return new CouponResponse.IssuedCouponDetail(
                            c.getId(), c.getCouponTemplateId(),
                            t.getName(), t.getDiscountType().name(),
                            t.getDiscountValue(), t.getMaxDiscountAmount(),
                            c.getStatus().name(), c.getUsedAt(), c.getCreatedAt());
                })
                .toList();

        return ApiResponse.success(new CouponResponse.CouponListResponse(details));
    }
}
