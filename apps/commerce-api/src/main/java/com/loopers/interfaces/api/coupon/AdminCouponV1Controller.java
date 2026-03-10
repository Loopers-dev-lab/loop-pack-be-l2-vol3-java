package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponService;
import com.loopers.application.coupon.IssuedCouponService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller {

    private final CouponService couponService;
    private final IssuedCouponService issuedCouponService;

    @GetMapping
    public ApiResponse<PageResponse<AdminCouponV1Dto.CouponResponse>> getCoupons(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AdminCouponV1Dto.CouponResponse> page = couponService.findAll(pageable)
            .map(AdminCouponV1Dto.CouponResponse::from);
        return ApiResponse.success(PageResponse.from(page));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> getCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId
    ) {
        Coupon coupon = couponService.findById(couponId);
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCouponV1Dto.CouponResponse> createCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @RequestBody AdminCouponV1Dto.CreateRequest request
    ) {
        Coupon coupon = couponService.create(
            request.name(),
            Coupon.DiscountType.valueOf(request.discountType()),
            request.discountValue(),
            request.minOrderAmount(),
            request.expiresAt()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<AdminCouponV1Dto.CouponResponse> updateCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId,
        @RequestBody AdminCouponV1Dto.UpdateRequest request
    ) {
        Coupon coupon = couponService.update(
            couponId,
            request.name(),
            request.discountValue(),
            request.minOrderAmount(),
            request.expiresAt()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @DeleteMapping("/{couponId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCoupon(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId
    ) {
        couponService.delete(couponId);
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<PageResponse<AdminCouponV1Dto.IssuedCouponResponse>> getCouponIssues(
        @RequestHeader("X-Loopers-Ldap") String ldap,
        @PathVariable Long couponId,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        Page<AdminCouponV1Dto.IssuedCouponResponse> page = issuedCouponService.findByCouponId(couponId, pageable)
            .map(AdminCouponV1Dto.IssuedCouponResponse::from);
        return ApiResponse.success(PageResponse.from(page));
    }
}
