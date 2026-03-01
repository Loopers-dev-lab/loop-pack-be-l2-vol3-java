package com.loopers.interfaces.api.coupon;

import com.loopers.application.coupon.CouponApplicationService;
import com.loopers.domain.PageResult;
import com.loopers.domain.coupon.Coupon;
import com.loopers.domain.coupon.CouponIssue;
import com.loopers.interfaces.api.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponV1Controller implements AdminCouponV1ApiSpec {

    private final CouponApplicationService couponApplicationService;

    @GetMapping
    @Override
    public ApiResponse<AdminCouponV1Dto.CouponPageResponse> getAllCoupons(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<Coupon> result = couponApplicationService.getAllCoupons(page, size);
        return ApiResponse.success(AdminCouponV1Dto.CouponPageResponse.from(result));
    }

    @GetMapping("/{couponId}")
    @Override
    public ApiResponse<AdminCouponV1Dto.CouponResponse> getCoupon(@PathVariable Long couponId) {
        Coupon coupon = couponApplicationService.getCoupon(couponId);
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @PostMapping
    @Override
    public ApiResponse<AdminCouponV1Dto.CouponResponse> createCoupon(
        @Valid @RequestBody AdminCouponV1Dto.CreateCouponRequest request
    ) {
        Coupon coupon = couponApplicationService.registerCoupon(
            request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @PutMapping("/{couponId}")
    @Override
    public ApiResponse<AdminCouponV1Dto.CouponResponse> updateCoupon(
        @PathVariable Long couponId,
        @Valid @RequestBody AdminCouponV1Dto.UpdateCouponRequest request
    ) {
        Coupon coupon = couponApplicationService.updateCoupon(
            couponId, request.name(), request.type(), request.value(),
            request.minOrderAmount(), request.expiredAt()
        );
        return ApiResponse.success(AdminCouponV1Dto.CouponResponse.from(coupon));
    }

    @DeleteMapping("/{couponId}")
    @Override
    public ApiResponse<Void> deleteCoupon(@PathVariable Long couponId) {
        couponApplicationService.deleteCoupon(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    @Override
    public ApiResponse<AdminCouponV1Dto.CouponIssuePageResponse> getCouponIssues(
        @PathVariable Long couponId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        PageResult<CouponIssue> result = couponApplicationService.getCouponIssues(couponId, page, size);
        return ApiResponse.success(AdminCouponV1Dto.CouponIssuePageResponse.from(result));
    }
}
