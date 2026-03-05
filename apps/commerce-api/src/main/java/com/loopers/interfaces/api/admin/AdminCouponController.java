package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponAppService;
import com.loopers.application.coupon.CouponInfo;
import com.loopers.application.coupon.IssuedCouponInfo;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.IssuedCoupon;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.resolver.LoginAdmin;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/v1/coupons")
@RequiredArgsConstructor
public class AdminCouponController {
    private final CouponAppService couponAppService;

    @PostMapping
    public ApiResponse<AdminCouponDto.CouponResponse> create(
            @LoginAdmin String adminId,
            @RequestBody AdminCouponDto.CreateRequest request) {
        Coupon coupon = couponAppService.create(
                request.name(), request.discountType(), request.toDiscountValue(),
                request.toMinOrderAmount(), request.toMaxDiscountAmount(),
                request.totalQuantity(), request.validFrom(), request.validUntil()
        );
        return ApiResponse.success(AdminCouponDto.CouponResponse.from(CouponInfo.from(coupon)));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCouponDto.CouponResponse> update(
            @LoginAdmin String adminId,
            @PathVariable Long id,
            @RequestBody AdminCouponDto.UpdateRequest request) {
        Coupon coupon = couponAppService.update(
                id, request.name(), request.discountType(), request.toDiscountValue(),
                request.toMinOrderAmount(), request.toMaxDiscountAmount(),
                request.totalQuantity(), request.validFrom(), request.validUntil()
        );
        return ApiResponse.success(AdminCouponDto.CouponResponse.from(CouponInfo.from(coupon)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        couponAppService.delete(id);
        return ApiResponse.success(null);
    }

    @GetMapping
    public ApiResponse<AdminCouponDto.CouponListResponse> getAll(
            @LoginAdmin String adminId,
            Pageable pageable) {
        Page<CouponInfo> couponInfoPage = couponAppService.getAll(pageable).map(CouponInfo::from);
        return ApiResponse.success(AdminCouponDto.CouponListResponse.from(couponInfoPage));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminCouponDto.CouponResponse> getById(
            @LoginAdmin String adminId,
            @PathVariable Long id) {
        Coupon coupon = couponAppService.getById(id);
        return ApiResponse.success(AdminCouponDto.CouponResponse.from(CouponInfo.from(coupon)));
    }

    @GetMapping("/{couponId}/issued")
    public ApiResponse<AdminCouponDto.IssuedCouponListResponse> getIssuedCoupons(
            @LoginAdmin String adminId,
            @PathVariable Long couponId,
            Pageable pageable) {
        Page<IssuedCouponInfo> infoPage = couponAppService.getIssuedCouponsByCouponId(couponId, pageable)
                .map(IssuedCouponInfo::from);
        return ApiResponse.success(AdminCouponDto.IssuedCouponListResponse.from(infoPage));
    }
}
