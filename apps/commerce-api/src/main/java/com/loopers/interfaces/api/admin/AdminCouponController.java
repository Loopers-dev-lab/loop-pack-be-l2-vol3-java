package com.loopers.interfaces.api.admin;

import com.loopers.application.coupon.CouponAdminApplicationService;
import com.loopers.domain.coupon.Coupon;
import com.loopers.interfaces.api.ApiResponse;
import com.loopers.interfaces.api.coupon.CouponAdminDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api-admin/v1/coupons")
public class AdminCouponController {

    private final CouponAdminApplicationService couponAdminApplicationService;

    @GetMapping
    public ApiResponse<CouponAdminDto.CouponListResponse> listCoupons(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Coupon> coupons = couponAdminApplicationService.list(pageable);
        return ApiResponse.success(CouponAdminDto.CouponListResponse.from(coupons));
    }

    @GetMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.CouponResponse> getCoupon(@PathVariable UUID couponId) {
        Coupon coupon = couponAdminApplicationService.findById(couponId);
        return ApiResponse.success(CouponAdminDto.CouponResponse.from(coupon));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CouponAdminDto.CouponResponse> createCoupon(
            @Valid @RequestBody CouponAdminDto.CreateCouponRequest request
    ) {
        Coupon coupon = couponAdminApplicationService.create(request.toCommand());
        return ApiResponse.success(CouponAdminDto.CouponResponse.from(coupon));
    }

    @PutMapping("/{couponId}")
    public ApiResponse<CouponAdminDto.CouponResponse> updateCoupon(
            @PathVariable UUID couponId,
            @Valid @RequestBody CouponAdminDto.UpdateCouponRequest request
    ) {
        Coupon coupon = couponAdminApplicationService.update(couponId, request.toCommand());
        return ApiResponse.success(CouponAdminDto.CouponResponse.from(coupon));
    }

    @DeleteMapping("/{couponId}")
    public ApiResponse<Void> deleteCoupon(@PathVariable UUID couponId) {
        couponAdminApplicationService.delete(couponId);
        return ApiResponse.success();
    }

    @GetMapping("/{couponId}/issues")
    public ApiResponse<CouponAdminDto.CouponIssueListResponse> listCouponIssues(
            @PathVariable UUID couponId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(
                CouponAdminDto.CouponIssueListResponse.from(couponAdminApplicationService.listIssues(couponId, pageable))
        );
    }
}
