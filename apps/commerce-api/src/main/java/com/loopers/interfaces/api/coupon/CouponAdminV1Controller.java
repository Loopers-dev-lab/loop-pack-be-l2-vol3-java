package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponIssueService;
import com.loopers.application.coupon.CouponService;
import com.loopers.domain.coupon.CouponModel;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.auth.AdminUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RequiredArgsConstructor
@RestController
public class CouponAdminV1Controller {

    private final CouponService couponService;
    private final CouponIssueService couponIssueService;

    @GetMapping("/api-admin/v1/coupons")
    public ApiResponse<Page<CouponAdminV1Dto.CouponResponse>> getCoupons(
        @AdminUser String adminLdap,
        Pageable pageable
    ) {
        Page<CouponModel> coupons = couponService.getAllCoupons(pageable);
        return ApiResponse.success(coupons.map(CouponAdminV1Dto.CouponResponse::from));
    }

    @GetMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> getCoupon(
        @AdminUser String adminLdap,
        @PathVariable Long couponId
    ) {
        CouponModel coupon = couponService.getCoupon(couponId);
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(coupon));
    }

    @PostMapping("/api-admin/v1/coupons")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> createCoupon(
        @AdminUser String adminLdap,
        @RequestBody CouponAdminV1Dto.CreateRequest request
    ) {
        CouponModel coupon = couponService.create(
            request.name(), request.type(), request.value(),
            request.toMinOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(coupon));
    }

    @PutMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<CouponAdminV1Dto.CouponResponse> updateCoupon(
        @AdminUser String adminLdap,
        @PathVariable Long couponId,
        @RequestBody CouponAdminV1Dto.UpdateRequest request
    ) {
        CouponModel coupon = couponService.update(
            couponId, request.name(), request.type(), request.value(),
            request.toMinOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(CouponAdminV1Dto.CouponResponse.from(coupon));
    }

    @DeleteMapping("/api-admin/v1/coupons/{couponId}")
    public ApiResponse<Void> deleteCoupon(
        @AdminUser String adminLdap,
        @PathVariable Long couponId
    ) {
        couponService.delete(couponId);
        return ApiResponse.success(null);
    }

    @GetMapping("/api-admin/v1/coupons/{couponId}/issues")
    public ApiResponse<Page<CouponV1Dto.CouponIssueResponse>> getCouponIssues(
        @AdminUser String adminLdap,
        @PathVariable Long couponId,
        Pageable pageable
    ) {
        return ApiResponse.success(
            couponIssueService.getIssuesByCoupon(couponId, pageable)
                .map(CouponV1Dto.CouponIssueResponse::from)
        );
    }
}
